package com.bubie.job;

import com.bubie.BubieConfig;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Incremental area scanner: inspects up to {@code scanPerTick} blocks per tick across the work area, caching up to
 * {@link #MAX_CANDIDATES} targets and returning the nearest valid unclaimed one.
 */
final class AreaScanner {
    private static final int MAX_CANDIDATES = 64;

    private final List<BlockPos> candidates = new ArrayList<>();
    private int scanLayer;
    private long scanCursor;
    private boolean foundThisPass, layersFinished;

    void reset() {
        candidates.clear();
        scanLayer = 0;
        scanCursor = 0;
        foundThisPass = false;
        layersFinished = false;
    }

    void start(WorkArea area) {
        reset();
        scanLayer = area.max().getY();
    }

    boolean isFinished() {
        return layersFinished;
    }

    void removeCandidate(BlockPos pos) {
        candidates.remove(pos);
    }

    /** Scans the area a slice at a time for the job's targets; picks the nearest found one. */
    @Nullable
    BlockPos next(ServerLevel level, Job job, WorkArea area, BubieEntity bubie, Predicate<BlockPos> blacklisted) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        Iterator<BlockPos> it = candidates.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!level.isLoaded(pos) || !job.isTarget(level, pos, level.getBlockState(pos))) {
                it.remove();
                continue;
            }
            if (blacklisted.test(pos) || Claims.takenByOther(level, pos, bubie.getId(), job.claimRadius())) continue;
            double d = bubie.distanceToSqr(Vec3.atCenterOf(pos));
            if (job.layered()) d -= pos.getY() * 4096.0; // finish the higher blocks of a layer first
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        if (best != null) return best;
        if (layersFinished) return JobRunner.NOTHING;

        int sx = area.sizeX(), sz = area.sizeZ();
        long volume = job.layered() ? (long) sx * sz : (long) sx * sz * area.sizeY();
        int budget = BubieConfig.SCAN_PER_TICK.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (budget-- > 0) {
            if (scanCursor >= volume) {
                boolean found = foundThisPass;
                scanCursor = 0;
                foundThisPass = false;
                if (!candidates.isEmpty()) return null; // try them next tick
                if (job.layered()) {
                    if (found) return null; // everything found was blacklisted for now; scan the layer again
                    scanLayer--;
                    if (scanLayer < area.min().getY()) {
                        layersFinished = true;
                        return JobRunner.NOTHING;
                    }
                    continue;
                }
                return JobRunner.NOTHING;
            }
            long i = scanCursor++;
            int x, y, z;
            if (job.layered()) {
                x = (int) (i % sx);
                z = (int) (i / sx);
                y = scanLayer;
            } else {
                // top down, so trees and crops are met from above
                long layerSize = (long) sx * sz;
                y = area.max().getY() - (int) (i / layerSize);
                long r = i % layerSize;
                x = (int) (r % sx);
                z = (int) (r / sx);
            }
            pos.set(area.min().getX() + x, y, area.min().getZ() + z);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (job.isProtected(pos) || !job.isTarget(level, pos, state)) continue;
            if (!job.instant() && !job.placesBlocks() && supportsBlockEntity(level, pos)) continue;
            foundThisPass = true;
            if (blacklisted.test(pos)) continue;
            candidates.add(pos.immutable());
            if (candidates.size() >= MAX_CANDIDATES) return null;
        }
        return null;
    }

    /** Never dig out the block a chest, a station or another block entity sits on. */
    private static boolean supportsBlockEntity(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos.above()) != null;
    }
}
