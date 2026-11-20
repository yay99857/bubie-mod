package com.bubie.job;

import com.bubie.BubieConfig;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.JobType;
import com.bubie.entity.MineMode;
import com.bubie.entity.Status;
import com.bubie.entity.WorkArea;
import com.bubie.item.ChipType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The work loop of one Bubie: SEEK a target → MOVE next to it → WORK on it (mining progress, cracks, energy) →
 * back to SEEK; DEPOSIT at the chest (or station) whenever storage fills up, and IDLE when there is nothing left.
 */
public class JobRunner {
    /** Returned by {@link Job#next} when there is no work right now. */
    public static final BlockPos NOTHING = new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    private static final int MAX_CANDIDATES = 64;

    private enum State { SEEK, MOVE, WORK, DEPOSIT, FETCH, IDLE }

    private final BubieEntity bubie;
    private State state = State.SEEK;
    @Nullable
    private Job job;
    @Nullable
    private WorkArea jobArea;
    private JobType jobType;
    private MineMode jobMineMode;
    private int jobFilter;

    @Nullable
    private BlockPos target;
    @Nullable
    private ItemEntity itemTarget;
    private float progress, effort;
    private int stateTicks, lastStage = -1;
    private boolean fluidsChecked;
    private long idleUntil;
    private boolean finishedAnnounced;
    private final Map<BlockPos, Long> blacklist = new HashMap<>();
    private final Set<Integer> skippedItems = new HashSet<>();

    // scanning
    private final List<BlockPos> candidates = new ArrayList<>();
    private int scanLayer;
    private long scanCursor;
    private boolean foundThisPass, layersFinished;

    // deposit
    @Nullable
    private BlockPos depositPos;
    private long closeChestAt = -1;
    @Nullable
    private BlockPos chestToClose;

    public JobRunner(BubieEntity bubie) {
        this.bubie = bubie;
    }

    // ------------------------------------------------------------------ lifecycle

    public void reset() {
        clearCrack();
        job = null;
        jobArea = null;
        state = State.SEEK;
        target = null;
        itemTarget = null;
        blacklist.clear();
        skippedItems.clear();
        candidates.clear();
        layersFinished = false;
        finishedAnnounced = false;
        idleUntil = 0;
    }

    /** Another goal (fleeing, resting) took over: drop the current step but remember the job. */
    public void pause() {
        clearCrack();
        progress = 0;
        if (state == State.WORK || state == State.MOVE) {
            state = State.SEEK;
            target = null;
            itemTarget = null;
        }
        bubie.getNavigation().stop();
    }

    public void release() {
        clearCrack();
    }

    @Nullable
    public BlockPos currentTarget() {
        return target;
    }

    public boolean isFinished() {
        return layersFinished;
    }

    private ServerLevel level() {
        return (ServerLevel) bubie.level();
    }

    private long now() {
        return bubie.level().getGameTime();
    }

    private Job buildJob(WorkArea area) {
        return switch (bubie.getJob()) {
            case LUMBERJACK -> new LumberJob(bubie, area);
            case DIGGER -> new DigJob(bubie, area, bubie.getDigFilter());
            case QUARRY -> new QuarryJob(bubie, area);
            case FARMER -> new FarmJob(bubie, area);
            case MINER -> new MinerJob(bubie, area, bubie.getMineMode());
            case COLLECTOR -> new CollectJob(bubie, area);
            case BUILDER -> new BuilderJob(bubie, area);
        };
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        if (bubie.level().isClientSide) return;
        tickChestLid();
        if (bubie.getJob() == JobType.BUILDER
                && (!com.bubie.compat.BlueprintCompat.loaded() || bubie.getBuildPlan().isEmpty())) {
            idle(Status.NO_BLUEPRINT);
            return;
        }
        WorkArea area = bubie.getWorkArea();
        if (area == null) {
            idle(Status.NO_AREA);
            return;
        }
        if (area.largestSide() > bubie.maxAreaSide()) {
            idle(Status.AREA_TOO_BIG);
            return;
        }
        if (job == null || !area.equals(jobArea) || jobType != bubie.getJob() || jobMineMode != bubie.getMineMode()
                || jobFilter != bubie.getDigFilter()) {
            reset();
            job = buildJob(area);
            jobArea = area;
            jobType = bubie.getJob();
            jobMineMode = bubie.getMineMode();
            jobFilter = bubie.getDigFilter();
            scanLayer = area.max().getY();
            scanCursor = 0;
            foundThisPass = false;
        }
        if (!bubie.hasEnergy()) {
            idle(Status.NO_ENERGY);
            return;
        }
        stateTicks++;
        switch (state) {
            case IDLE -> {
                if (now() >= idleUntil) setState(State.SEEK);
            }
            case SEEK -> seek();
            case MOVE -> move();
            case WORK -> work();
            case DEPOSIT -> deposit();
            case FETCH -> fetch();
        }
    }

    private void idle(Status status) {
        bubie.getNavigation().stop();
        bubie.setStatus(status);
        clearCrack();
    }

    private void setState(State next) {
        state = next;
        stateTicks = 0;
    }

    private void rest(Status status, int ticks) {
        bubie.setStatus(status);
        bubie.getNavigation().stop();
        idleUntil = now() + ticks;
        setState(State.IDLE);
    }

    private void fail(BlockPos pos, int ticks) {
        blacklist.put(pos.immutable(), now() + ticks);
        clearCrack();
        target = null;
        bubie.getNavigation().stop();
        setState(State.SEEK);
    }

    public boolean isBlacklisted(BlockPos pos) {
        return blacklisted(pos);
    }

    private boolean blacklisted(BlockPos pos) {
        Long until = blacklist.get(pos);
        if (until == null) return false;
        if (until <= now()) {
            blacklist.remove(pos);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ SEEK

    private void seek() {
        if (!bubie.hasFreeSlot() && depositableItems()) {
            startDeposit(true);
            return;
        }
        if (job.collectsItems()) {
            ItemEntity item = nearestLooseItem();
            if (item != null) {
                itemTarget = item;
                target = null;
                bubie.setStatus(Status.COLLECTING);
                setState(State.MOVE);
                return;
            }
        }
        BlockPos next = job.next(this);
        if (next == null) {
            bubie.setStatus(Status.WORKING);
            return; // still scanning
        }
        if (next == NOTHING) {
            if (bubie.hasStoredItems() && resolveDeposit() != null && depositableItems()) {
                startDeposit(false);
                return;
            }
            if (job.layered() && layersFinished || job.isDone()) {
                if (!finishedAnnounced) {
                    finishedAnnounced = true;
                    bubie.showMood(Face.HAPPY, 60);
                    bubie.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.4F);
                }
                rest(Status.DONE, 200);
            } else {
                rest(Status.NOTHING_TO_DO, 100);
            }
            return;
        }
        // material first: a trip to the chest if the Bubie has none of what this spot takes
        Item needed = job.requiredItem(next);
        if (needed != null && bubie.count(needed) == 0) {
            startFetch(needed);
            return;
        }
        target = next;
        itemTarget = null;
        progress = 0;
        fluidsChecked = false;
        bubie.setStatus(Status.WORKING);
        setState(State.MOVE);
    }

    @Nullable
    private ItemEntity nearestLooseItem() {
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, jobArea.box().inflate(1.0, 2.0, 1.0))) {
            if (!item.isAlive() || item.hasPickUpDelay() || skippedItems.contains(item.getId())) continue;
            double d = item.distanceToSqr(bubie);
            if (d < bestDist) {
                bestDist = d;
                best = item;
            }
        }
        return best;
    }

    /** Scans the area a slice at a time for the job's targets; picks the nearest found one. */
    @Nullable
    public BlockPos scanNext() {
        ServerLevel level = level();
        // nearest still-valid candidate
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        Iterator<BlockPos> it = candidates.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!level.isLoaded(pos) || !job.isTarget(level, pos, level.getBlockState(pos))) {
                it.remove();
                continue;
            }
            if (blacklisted(pos)) continue;
            double d = bubie.distanceToSqr(Vec3.atCenterOf(pos));
            if (job.layered()) d -= pos.getY() * 4096.0; // finish the higher blocks of a layer first
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        if (best != null) return best;
        if (layersFinished) return NOTHING;

        WorkArea a = jobArea;
        int sx = a.sizeX(), sz = a.sizeZ();
        long volume = job.layered() ? (long) sx * sz : (long) sx * sz * a.sizeY();
        int budget = BubieConfig.SCAN_PER_TICK.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (budget-- > 0) {
            if (scanCursor >= volume) {
                // end of a pass
                boolean found = foundThisPass;
                scanCursor = 0;
                foundThisPass = false;
                if (!candidates.isEmpty()) return null; // try them next tick
                if (job.layered()) {
                    if (found) return null; // everything found was blacklisted for now; scan the layer again
                    scanLayer--;
                    if (scanLayer < a.min().getY()) {
                        layersFinished = true;
                        return NOTHING;
                    }
                    continue;
                }
                return NOTHING;
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
                y = a.max().getY() - (int) (i / layerSize);
                long r = i % layerSize;
                x = (int) (r % sx);
                z = (int) (r / sx);
            }
            pos.set(a.min().getX() + x, y, a.min().getZ() + z);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (job.isProtected(pos) || !job.isTarget(level, pos, state)) continue;
            if (supportsBlockEntity(level, pos)) continue;
            foundThisPass = true;
            if (blacklisted(pos)) continue;
            candidates.add(pos.immutable());
            if (candidates.size() >= MAX_CANDIDATES) return null;
        }
        return null;
    }

    /** Never dig out the block a chest, a station or another block entity sits on. */
    private static boolean supportsBlockEntity(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos.above()) != null;
    }

    // ------------------------------------------------------------------ MOVE

    private void move() {
        if (itemTarget != null) {
            if (!itemTarget.isAlive()) {
                itemTarget = null;
                setState(State.SEEK);
                return;
            }
            if (stateTicks % 10 == 1) bubie.getNavigation().moveTo(itemTarget, 1.1);
            if (stateTicks > 200) {
                skippedItems.add(itemTarget.getId());
                itemTarget = null;
                setState(State.SEEK);
            }
            return;
        }
        if (target == null) {
            setState(State.SEEK);
            return;
        }
        bubie.setStatus(Status.WORKING);
        if (bubie.canReach(target)) {
            bubie.getNavigation().stop();
            setState(State.WORK);
            effort = Math.max(1.0F, job.effort(level(), target));
            return;
        }
        if (stateTicks == 1 || (stateTicks % 20 == 0 && bubie.getNavigation().isDone())) {
            Path path = bubie.getNavigation().createPath(target, 2);
            if (path == null || !path.canReach() && !endsNear(path, target)) {
                fail(target, 1200);
                return;
            }
            bubie.getNavigation().moveTo(path, 1.0);
        }
        if (stateTicks > 400) fail(target, 1200);
    }

    private static boolean endsNear(Path path, BlockPos target) {
        Node end = path.getEndNode();
        return end != null && end.asBlockPos().distSqr(target) <= 16;
    }

    // ------------------------------------------------------------------ WORK

    private void work() {
        if (job.placesBlocks()) {
            workPlace();
            return;
        }
        ServerLevel level = level();
        BlockState state = level.getBlockState(target);
        if (!job.isTarget(level, target, state)) {
            clearCrack();
            target = null;
            setState(State.SEEK);
            return;
        }
        if (!bubie.canReach(target)) {
            setState(State.MOVE);
            return;
        }
        bubie.lookAtBlock(target);
        bubie.setStatus(Status.WORKING);

        boolean harvest = job.isHarvest(state);
        if (!harvest && bubie.isUnderFeet(target)) {
            // standing on it: step aside first
            if (stateTicks % 20 == 1) {
                Vec3 spot = LandRandomPos.getPos(bubie, 3, 2);
                if (spot != null) bubie.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
            }
            if (stateTicks > 80) fail(target, 200);
            return;
        }
        if (!harvest && !fluidsChecked && job.plugsFluids()) {
            if (!plugFluids(target)) {
                bubie.setStatus(Status.NEED_FILLER);
                fail(target, 600);
                rest(Status.NEED_FILLER, 60);
                return;
            }
            fluidsChecked = true;
        }

        ItemStack tool = ItemStack.EMPTY;
        if (!harvest) {
            ItemStack own = bubie.getTool();
            boolean worn = own.isDamageableItem() && own.getMaxDamage() - own.getDamageValue() <= 2;
            boolean usable = !own.isEmpty() && !worn;
            if (state.requiresCorrectToolForDrops() && (!usable || !own.isCorrectToolForDrops(state))) {
                Status why = worn ? Status.TOOL_LOW : Status.NO_TOOL;
                fail(target, 600);
                // Jobs that must dig everything wait for a tool; the others skip this block.
                if (job.layered() || job instanceof MinerJob) rest(why, 100);
                else bubie.setStatus(why);
                return;
            }
            if (usable) tool = own;
        }

        float perTick = harvest ? 1.0F : BlockBreaker.progressPerTick(bubie, target, state, tool);
        if (perTick <= 0) {
            fail(target, 6000); // unbreakable
            return;
        }
        double speed = BubieConfig.BREAK_SPEED.get() * (1 + 0.5 * bubie.chipCount(ChipType.SPEED));
        progress += (float) (perTick * speed / effort);
        bubie.spendEnergy(1.0);
        if (stateTicks % 6 == 1) {
            bubie.swing(InteractionHand.MAIN_HAND);
            if (!harvest) {
                var sound = state.getSoundType(level, target, bubie);
                level.playSound(null, target, sound.getHitSound(), SoundSource.NEUTRAL, sound.getVolume() * 0.4F, sound.getPitch() * 0.6F);
            }
        }
        int stage = Math.min(9, (int) (progress * 10));
        if (!harvest && stage != lastStage) {
            level.destroyBlockProgress(bubie.getId(), target, stage);
            lastStage = stage;
        }
        if (progress < 1.0F) return;

        clearCrack();
        BlockPos done = target;
        boolean ok = job.perform(level, done, state, tool);
        progress = 0;
        target = null;
        if (!ok) {
            fail(done, 2400);
            return;
        }
        candidates.remove(done);
        setState(State.SEEK);
    }

    /** Before digging next to water or lava, plug it with a filler block. False if there is nothing to plug it with. */
    private boolean plugFluids(BlockPos pos) {
        ServerLevel level = level();
        for (Direction dir : Direction.values()) {
            BlockPos side = pos.relative(dir);
            if (level.getFluidState(side).isEmpty()) continue;
            ItemStack filler = bubie.takeOne(Job::isFiller);
            if (filler.isEmpty()) return false;
            if (!BlockBreaker.place(bubie, side, filler)) return false;
        }
        return true;
    }

    private void clearCrack() {
        if (lastStage >= 0 && target != null && bubie.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(bubie.getId(), target, -1);
        }
        lastStage = -1;
    }

    // ------------------------------------------------------------------ PLACE (builder)

    private void workPlace() {
        ServerLevel level = level();
        BlockState state = level.getBlockState(target);
        if (!job.isTarget(level, target, state)) {
            target = null;
            setState(State.SEEK);
            return;
        }
        if (!bubie.canReach(target)) {
            setState(State.MOVE);
            return;
        }
        AABB spot = new AABB(target);
        if (bubie.getBoundingBox().intersects(spot)) {
            // standing where the block goes: step aside first
            if (stateTicks % 20 == 1) {
                Vec3 away = LandRandomPos.getPos(bubie, 3, 2);
                if (away != null) bubie.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
            }
            if (stateTicks > 80) fail(target, 200);
            return;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, spot).isEmpty()) {
            if (stateTicks > 100) fail(target, 200); // something else stands there: come back to it later
            return;
        }
        bubie.getNavigation().stop();
        bubie.lookAtBlock(target);
        bubie.setStatus(Status.WORKING);
        progress += (float) ((1 + 0.5 * bubie.chipCount(ChipType.SPEED)) / 6.0);
        bubie.spendEnergy(1.0);
        if (stateTicks % 6 == 1) bubie.swing(InteractionHand.MAIN_HAND);
        if (progress < 1.0F) return;
        BlockPos done = target;
        progress = 0;
        target = null;
        if (!job.perform(level, done, state, ItemStack.EMPTY)) {
            fail(done, 600);
            return;
        }
        setState(State.SEEK);
    }

    // ------------------------------------------------------------------ FETCH (builder)

    @Nullable
    private Item fetchFor;

    private void startFetch(Item item) {
        depositPos = resolveDeposit();
        if (depositPos == null) {
            rest(Status.NO_CHEST, 100);
            return;
        }
        fetchFor = item;
        bubie.setStatus(Status.FETCHING);
        setState(State.FETCH);
    }

    /** Walks next to a container; true once it is there. */
    private boolean approach(BlockPos pos) {
        if (bubie.distanceToSqr(Vec3.atCenterOf(pos)) <= 2.8 * 2.8) {
            bubie.getNavigation().stop();
            bubie.lookAtBlock(pos);
            return true;
        }
        if (stateTicks % 20 == 1 || bubie.getNavigation().isDone()) {
            Path path = bubie.getNavigation().createPath(pos, 1);
            if (path != null) bubie.getNavigation().moveTo(path, 1.1);
        }
        if (stateTicks > 300 && BubieConfig.TELEPORT_WHEN_STUCK.get()) {
            bubie.teleportNear(pos);
            stateTicks = 0;
        } else if (stateTicks > 600) {
            rest(Status.NO_CHEST, 200);
        }
        return false;
    }

    /** Takes what the job still needs from the chest (as much as fits), then back to work. */
    private void fetch() {
        bubie.setStatus(Status.FETCHING);
        if (depositPos == null || fetchFor == null) {
            setState(State.SEEK);
            return;
        }
        if (!approach(depositPos)) return;
        IItemHandler source = handler(depositPos);
        if (source == null) {
            rest(Status.NO_CHEST, 100);
            return;
        }
        boolean took = false;
        for (Map.Entry<Item, Integer> want : job.shoppingList().entrySet()) {
            int need = want.getValue() - bubie.count(want.getKey());
            for (int slot = 0; slot < source.getSlots() && need > 0; slot++) {
                if (!source.getStackInSlot(slot).is(want.getKey())) continue;
                ItemStack got = source.extractItem(slot, Math.min(need, 64), false);
                if (got.isEmpty()) continue;
                ItemStack rest = bubie.insert(got);
                int taken = got.getCount() - rest.getCount();
                need -= taken;
                if (taken > 0) took = true;
                if (!rest.isEmpty()) {
                    ItemHandlerHelper.insertItemStacked(source, rest, false); // full: put the rest back
                    need = 0;
                }
            }
        }
        if (took) openChestLid(depositPos);
        if (bubie.count(fetchFor) == 0) {
            rest(Status.NO_MATERIAL, 100); // another Bubie (or the owner) can fill the chest meanwhile
            return;
        }
        setState(State.SEEK);
    }

    // ------------------------------------------------------------------ DEPOSIT

    @Nullable
    private BlockPos resolveDeposit() {
        BlockPos chest = bubie.getChest();
        if (chest != null && level().isLoaded(chest) && handler(chest) != null) return chest;
        BlockPos home = bubie.getHome();
        if (home != null && level().isLoaded(home) && handler(home) != null) return home;
        return null;
    }

    @Nullable
    private IItemHandler handler(BlockPos pos) {
        // The face toward the Bubie first: some blocks (Mekanism bins, sided machines) give nothing for "no side".
        Vec3 from = bubie.position().subtract(Vec3.atCenterOf(pos));
        Direction facing = Direction.getNearest(from.x, from.y, from.z);
        IItemHandler handler = level().getCapability(Capabilities.ItemHandler.BLOCK, pos, facing);
        if (handler == null) handler = level().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        for (Direction dir : Direction.values()) {
            if (handler != null) break;
            handler = level().getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
        }
        return handler;
    }

    /** Anything beyond what the job keeps (seeds, saplings, cobblestone)? */
    private boolean depositableItems() {
        Map<Item, Integer> kept = new HashMap<>();
        for (int i = 0; i < BubieEntity.STORAGE_SLOTS; i++) {
            ItemStack stack = bubie.storage().getItem(i);
            if (stack.isEmpty()) continue;
            int keepLeft = job.keep(stack) - kept.getOrDefault(stack.getItem(), 0);
            if (stack.getCount() > keepLeft) return true;
            kept.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return false;
    }

    private void startDeposit(boolean full) {
        depositPos = resolveDeposit();
        if (depositPos == null) {
            rest(full ? Status.NO_CHEST : Status.NOTHING_TO_DO, 100);
            return;
        }
        bubie.setStatus(Status.DEPOSITING);
        setState(State.DEPOSIT);
    }

    private void deposit() {
        bubie.setStatus(Status.DEPOSITING);
        if (depositPos == null) {
            setState(State.SEEK);
            return;
        }
        double dist = bubie.distanceToSqr(Vec3.atCenterOf(depositPos));
        if (dist > 2.8 * 2.8) {
            if (stateTicks % 20 == 1 || bubie.getNavigation().isDone()) {
                Path path = bubie.getNavigation().createPath(depositPos, 1);
                if (path != null) bubie.getNavigation().moveTo(path, 1.1);
            }
            if (stateTicks > 300 && BubieConfig.TELEPORT_WHEN_STUCK.get()) {
                bubie.teleportNear(depositPos);
                stateTicks = 0;
            } else if (stateTicks > 600) {
                rest(Status.NO_CHEST, 200);
            }
            return;
        }
        bubie.getNavigation().stop();
        bubie.lookAtBlock(depositPos);
        IItemHandler target = handler(depositPos);
        if (target == null) {
            rest(Status.NO_CHEST, 100);
            return;
        }
        boolean moved = false, leftover = false;
        Map<Item, Integer> kept = new HashMap<>();
        for (int i = 0; i < BubieEntity.STORAGE_SLOTS; i++) {
            ItemStack stack = bubie.storage().getItem(i);
            if (stack.isEmpty()) continue;
            // coal it found tops up its own fuel slot (up to 16); logs and the rest go to the chest
            if (BubieConfig.REQUIRE_ENERGY.get() && stack.is(ItemTags.COALS)) {
                ItemStack fuel = bubie.extra().getItem(BubieEntity.FUEL_SLOT);
                if (fuel.isEmpty()) {
                    bubie.extra().setItem(BubieEntity.FUEL_SLOT, stack.split(Math.min(16, stack.getCount())));
                } else if (ItemStack.isSameItemSameComponents(fuel, stack) && fuel.getCount() < 16) {
                    int n = Math.min(stack.getCount(), 16 - fuel.getCount());
                    fuel.grow(n);
                    stack.shrink(n);
                }
                if (stack.isEmpty()) continue;
            }
            int keepLeft = Math.max(0, job.keep(stack) - kept.getOrDefault(stack.getItem(), 0));
            int keepHere = Math.min(keepLeft, stack.getCount());
            kept.merge(stack.getItem(), keepHere, Integer::sum);
            if (stack.getCount() <= keepHere) continue;
            ItemStack out = stack.copyWithCount(stack.getCount() - keepHere);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(target, out, false);
            int sent = out.getCount() - rest.getCount();
            if (sent > 0) {
                stack.shrink(sent);
                moved = true;
            }
            if (!rest.isEmpty()) leftover = true;
        }
        bubie.storage().setChanged();
        bubie.extra().setChanged();
        if (moved) {
            bubie.showMood(Face.HAPPY, 30);
            openChestLid(depositPos);
        }
        if (leftover && (!moved || !bubie.hasFreeSlot())) {
            rest(Status.CHEST_FULL, 200);
            return;
        }
        setState(State.SEEK);
    }

    private void openChestLid(BlockPos pos) {
        Block block = level().getBlockState(pos).getBlock();
        if (!(block instanceof ChestBlock)) {
            level().playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.2F);
            return;
        }
        level().blockEvent(pos, block, 1, 1);
        level().playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4F, 1.1F);
        chestToClose = pos;
        closeChestAt = now() + 15;
    }

    private void tickChestLid() {
        if (chestToClose == null || now() < closeChestAt) return;
        BlockState state = level().getBlockState(chestToClose);
        if (state.getBlock() instanceof ChestBlock) {
            level().blockEvent(chestToClose, state.getBlock(), 1, 0);
            level().playSound(null, chestToClose, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4F, 1.1F);
        }
        chestToClose = null;
    }
}
