package com.bubie.job;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.MineMode;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Miner. VEIN: mines ores in the area that are open to the air (each one mined opens the next, so whole veins go).
 * TUNNEL: digs a 1×2 tunnel along the long side of the area at its bottom, with side branches every 4 blocks
 * (branch mining), takes every ore it uncovers along the way, and lights the tunnel with torches it carries.
 * Which ores it can take depends on the pickaxe it holds; ores come from the c:ores tag, so modded ores count.
 */
public class MinerJob extends Job {
    private static final int MAX_VEIN_QUEUE = 128;

    private final MineMode mode;
    private final List<BlockPos> plan = new ArrayList<>();
    private final Set<BlockPos> planned = new HashSet<>();
    private final Set<BlockPos> torchSpots = new HashSet<>();
    private final ArrayDeque<BlockPos> ores = new ArrayDeque<>();
    private int planIndex;

    public MinerJob(BubieEntity bubie, WorkArea area, MineMode mode) {
        super(bubie, area);
        this.mode = mode;
        if (mode == MineMode.TUNNEL) buildPlan();
    }

    @Override
    public boolean isDone() {
        return tunnelDone();
    }

    public boolean tunnelDone() {
        return mode == MineMode.TUNNEL && planIndex >= plan.size() && ores.isEmpty();
    }

    private void buildPlan() {
        boolean alongX = area.sizeX() >= area.sizeZ();
        int length = alongX ? area.sizeX() : area.sizeZ();
        int width = alongX ? area.sizeZ() : area.sizeX();
        int half = (width - 1) / 2;
        int y = area.min().getY();
        BlockPos start = alongX ? new BlockPos(area.min().getX(), y, area.min().getZ() + half)
                : new BlockPos(area.min().getX() + half, y, area.min().getZ());
        Direction forward = alongX ? Direction.EAST : Direction.SOUTH;
        Direction left = forward.getCounterClockWise(), right = forward.getClockWise();
        int leftLen = half, rightLen = width - 1 - half;
        for (int i = 0; i < length; i++) {
            BlockPos main = start.relative(forward, i);
            addCell(main);
            if (i % 8 == 4) torchSpots.add(main);
            if (i > 0 && i % 4 == 0) {
                for (int j = 1; j <= leftLen; j++) addCell(main.relative(left, j));
                for (int j = 1; j <= rightLen; j++) addCell(main.relative(right, j));
            }
        }
    }

    /** Upper block first, so gravel above can't fall into the gap the Bubie stands in. */
    private void addCell(BlockPos floor) {
        for (BlockPos pos : new BlockPos[]{floor.above(), floor}) {
            if (planned.add(pos)) plan.add(pos);
        }
    }

    private static boolean isOre(BlockState state) {
        return state.is(Tags.BlockItems.ORES.block());
    }

    private boolean exposed(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockState side = level.getBlockState(pos.relative(dir));
            if (side.isAir() || !side.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    @Override
    public boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0) return false;
        if (isOre(state)) return mode == MineMode.TUNNEL || exposed(level, pos);
        // tunnel blocks: anything in the way that isn't a block entity
        return mode == MineMode.TUNNEL && planned.contains(pos) && !state.hasBlockEntity()
                && !state.getCollisionShape(level, pos).isEmpty(); // torches and grass can stay
    }

    @Override
    public BlockPos next(JobRunner runner) {
        if (mode == MineMode.VEIN) return runner.scanNext();
        ServerLevel level = (ServerLevel) bubie.level();
        while (!ores.isEmpty()) {
            BlockPos ore = ores.peek();
            if (isOre(level.getBlockState(ore)) && !runner.isBlacklisted(ore)) return ore;
            ores.poll();
        }
        while (planIndex < plan.size()) {
            BlockPos pos = plan.get(planIndex);
            if (isTarget(level, pos, level.getBlockState(pos))) {
                return runner.isBlacklisted(pos) ? JobRunner.NOTHING : pos; // waits (e.g. for a pickaxe)
            }
            planIndex++;
            lightUp(level, pos);
        }
        return JobRunner.NOTHING;
    }

    @Override
    public boolean perform(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        if (!super.perform(level, pos, state, tool)) {
            ores.remove(pos);
            return false;
        }
        if (mode == MineMode.TUNNEL) {
            ores.remove(pos);
            // ores uncovered around this block join the queue (whole veins, one block at a time)
            for (Direction dir : Direction.values()) {
                BlockPos side = pos.relative(dir);
                if (ores.size() < MAX_VEIN_QUEUE && !ores.contains(side) && isOre(level.getBlockState(side))) ores.add(side);
            }
            lightUp(level, pos);
        }
        return true;
    }

    private void lightUp(ServerLevel level, BlockPos pos) {
        if (!torchSpots.contains(pos) || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) return;
        ItemStack torch = bubie.takeOne(s -> s.is(Items.TORCH));
        if (!torch.isEmpty()) BlockBreaker.place(bubie, pos, torch);
        torchSpots.remove(pos);
    }

    @Override
    public int keep(ItemStack stack) {
        if (stack.is(Items.TORCH)) return 64;
        return isFiller(stack) ? 64 : 0;
    }
}
