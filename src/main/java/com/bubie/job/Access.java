package com.bubie.job;

import com.bubie.entity.BubieEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Making a way where there is none, as a player would: when no path leads to where the Bubie has to be, it changes the
 * terrain one block at a time (dig a step down, cut a step up a wall, dig under its own feet in a narrow pit, bridge
 * a gap, tunnel through) and then tries to walk again.
 * <p>
 * Only natural blocks are ever broken (stone, dirt, sand, gravel, ores, terracotta, natural leaves), never anything
 * with a block entity or that looks built. Fluids next to a block are plugged before it is opened. Blocks it had to
 * break that were not part of the job itself are remembered as scars, and once the job runs out of work it fills
 * them back in, farthest from the way out first, so it never closes the path behind it.
 */
final class Access {
    /** Terrain changes it may make for one goal before giving up on it. */
    static final int MAX_STEPS = 64;
    /** Never drops into a hole deeper than this when digging under its feet. */
    private static final int MAX_DROP = 4;

    private final BubieEntity bubie;
    /** Blocks broken to get somewhere that the job would not have broken, and what they were. */
    private final Map<BlockPos, BlockState> scars = new LinkedHashMap<>();
    @Nullable
    private BlockPos stepsFor;
    private int steps;
    @Nullable
    private BlockPos restoring;
    private int restoreTicks;

    Access(BubieEntity bubie) {
        this.bubie = bubie;
    }

    void reset() {
        stepsFor = null;
        steps = 0;
        restoring = null;
        restoreTicks = 0;
    }

    int stepsTaken(BlockPos goal) {
        return goal.equals(stepsFor) ? steps : 0;
    }

    boolean hasScars() {
        return !scars.isEmpty();
    }

    int scarCount() {
        return scars.size();
    }

    // ------------------------------------------------------------------ making a way

    /**
     * One change to the terrain that brings the Bubie closer to being able to reach {@code goal}. False when there is
     * nothing sensible (or allowed) to do: the caller gives up on the goal as before.
     */
    boolean step(ServerLevel level, BlockPos goal, Job job) {
        if (!goal.equals(stepsFor)) {
            stepsFor = goal.immutable();
            steps = 0;
        }
        if (steps >= MAX_STEPS) return false;
        boolean done = tryStep(level, goal, job);
        if (done) {
            steps++;
            bubie.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
            bubie.spendEnergy(20);
        }
        return done;
    }

    private boolean tryStep(ServerLevel level, BlockPos goal, Job job) {
        BlockPos feet = bubie.blockPosition();
        int dx = goal.getX() - feet.getX(), dz = goal.getZ() - feet.getZ(), dy = goal.getY() - feet.getY();
        boolean far = Math.max(Math.abs(dx), Math.abs(dz)) > 1;
        Direction toward = Math.abs(dx) >= Math.abs(dz)
                ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);

        if (dy < -1 && !far) {
            // the goal is right under it (a narrow pit): dig down where it stands
            return digUnderFeet(level, feet, job);
        }
        if (dy < -1) {
            // a step down toward the goal: clear the spot ahead and the one under it, make sure there is a floor
            BlockPos ahead = feet.relative(toward);
            if (!passable(level, ahead)) return dig(level, ahead, job);
            if (!passable(level, ahead.below())) return dig(level, ahead.below(), job);
            if (!sturdy(level, ahead.below(2))) return fill(level, ahead.below(2));
            return walk(ahead.below());
        }
        if (dy > 1) {
            // a step up toward the goal: room over its head, something to stand on ahead, room over that
            Direction side = far ? toward : climbSide(level, feet);
            if (side == null) return false;
            BlockPos ahead = feet.relative(side);
            if (passable(level, ahead) && sturdy(level, ahead.below())) return walk(ahead); // flat ground first: walk on
            if (!passable(level, feet.above())) return dig(level, feet.above(), job);
            if (!sturdy(level, ahead)) return fill(level, ahead);
            if (!passable(level, ahead.above())) return dig(level, ahead.above(), job);
            return walk(ahead.above());
        }
        if (!far) return false;
        // same height: tunnel through what is in the way, bridge a gap in the floor
        BlockPos ahead = feet.relative(toward);
        if (!passable(level, ahead)) return dig(level, ahead, job);
        if (!sturdy(level, ahead.below())) {
            BlockPos under = ahead.below();
            if (sturdy(level, under.below()) && passable(level, under)) return walk(under); // a step down into it
            if (!passable(level, under)) return false;
            return fill(level, under);
        }
        return walk(ahead);
    }

    /** One block on, now that the way is open: straight there (pathfinding gives up in one-block tunnels). */
    private boolean walk(BlockPos to) {
        bubie.getNavigation().stop();
        bubie.getMoveControl().setWantedPosition(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.0);
        return true;
    }

    /** Toward a spot along the way it opened: by path when there is one, else a block at a time without digging. */
    private void head(ServerLevel level, BlockPos to) {
        net.minecraft.world.level.pathfinder.Path path = bubie.getNavigation().createPath(to, 1);
        if (path != null && path.canReach()) {
            bubie.getNavigation().moveTo(path, 1.0);
            return;
        }
        BlockPos feet = bubie.blockPosition();
        int dx = to.getX() - feet.getX(), dz = to.getZ() - feet.getZ(), dy = to.getY() - feet.getY();
        BlockPos best = null;
        double bestDist = Vec3.atCenterOf(feet).distanceToSqr(Vec3.atCenterOf(to));
        // the open neighbour (level, a step up or a step down) that is closest to where it is going
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int up = -1; up <= 1; up++) {
                BlockPos n = feet.relative(dir).above(up);
                if (!passable(level, n) || !sturdy(level, n.below())) continue;
                if (up > 0 && !passable(level, feet.above())) continue;
                double d = Vec3.atCenterOf(n).distanceToSqr(Vec3.atCenterOf(to));
                if (d < bestDist) {
                    bestDist = d;
                    best = n;
                }
            }
        }
        if (best != null) walk(best);
    }

    /** Which side to cut steps into when the goal is straight above: the first wall it can use. */
    @Nullable
    private Direction climbSide(ServerLevel level, BlockPos feet) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos ahead = feet.relative(dir);
            if ((sturdy(level, ahead) || canFill(level, ahead)) && (passable(level, ahead.above()) || breakable(level, ahead.above()))) {
                return dir;
            }
        }
        return null;
    }

    private boolean digUnderFeet(ServerLevel level, BlockPos feet, Job job) {
        BlockPos below = feet.below();
        if (!safeDrop(level, below)) return false;
        return dig(level, below, job);
    }

    /** Opening this block drops the Bubie onto a floor close below, not into a fluid or a deep hole. */
    static boolean safeDrop(ServerLevel level, BlockPos opened) {
        for (int d = 1; d <= MAX_DROP; d++) {
            BlockPos at = opened.below(d);
            if (!level.getFluidState(at).isEmpty()) return false;
            if (sturdy(level, at)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ the changes themselves

    private boolean dig(ServerLevel level, BlockPos pos, Job job) {
        if (!breakable(level, pos)) return false;
        if (!plugAround(level, pos)) return false;
        BlockState before = level.getBlockState(pos);
        boolean wanted = job.area.contains(pos) && job.isTarget(level, pos, before);
        if (!BlockBreaker.breakBlock(bubie, pos, bubie.getTool())) return false;
        // what the job digs anyway is not a scar; natural leaves grow back, they are not one either
        if (!wanted && !(before.getBlock() instanceof LeavesBlock) && !scars.containsKey(pos)) scars.put(pos.immutable(), before);
        return true;
    }

    private boolean fill(ServerLevel level, BlockPos pos) {
        if (!canFill(level, pos) || occupied(pos)) return false;
        ItemStack filler = bubie.takeOne(Job::isFiller);
        if (filler.isEmpty()) return false;
        return BlockBreaker.place(bubie, pos, filler);
    }

    private boolean canFill(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced() && hasFiller();
    }

    private boolean hasFiller() {
        for (int i = 0; i < BubieEntity.STORAGE_SLOTS; i++) if (Job.isFiller(bubie.storage().getItem(i))) return true;
        return false;
    }

    /** Water and lava next to a block it is about to open get plugged first. */
    private boolean plugAround(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos side = pos.relative(dir);
            if (level.getFluidState(side).isEmpty()) continue;
            ItemStack filler = bubie.takeOne(Job::isFiller);
            if (filler.isEmpty() || !BlockBreaker.place(bubie, side, filler)) return false;
        }
        return true;
    }

    private boolean occupied(BlockPos pos) {
        return bubie.getBoundingBox().intersects(new AABB(pos));
    }

    static boolean passable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }

    static boolean sturdy(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }

    /** Natural ground only, never anything with a block entity, never the floor under a block entity. */
    static boolean breakable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) return false;
        if (level.getBlockEntity(pos.above()) != null) return false;
        if (state.getBlock() instanceof LeavesBlock) return !state.getValue(LeavesBlock.PERSISTENT);
        return natural(state);
    }

    static boolean natural(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.SOUL_SOIL) || state.is(Tags.BlockItems.ORES.block())
                || state.is(Tags.BlockItems.COBBLESTONES.block()) || state.is(Blocks.COBBLED_DEEPSLATE);
    }

    // ------------------------------------------------------------------ closing the way again

    /**
     * Fills the scars back in, one at a time. {@code exit} is where it is heading when done (its chest or home):
     * the scar farthest from it goes first, so the way out stays open until last. True while there is work left.
     */
    boolean restore(ServerLevel level, @Nullable BlockPos exit) {
        scars.keySet().removeIf(pos -> !passable(level, pos)); // filled by someone else
        if (scars.isEmpty()) {
            restoring = null;
            return false;
        }
        Vec3 out = exit != null ? Vec3.atCenterOf(exit) : bubie.position();
        List<BlockPos> order = new ArrayList<>(scars.keySet());
        order.sort(Comparator.comparingDouble((BlockPos p) -> Vec3.atCenterOf(p).distanceToSqr(out)).reversed());
        BlockPos next = order.get(0);
        if (!next.equals(restoring)) {
            restoring = next;
            restoreTicks = 0;
        }
        restoreTicks++;
        if (restoreTicks > 400) {
            scars.remove(next); // can't get to it: leave that one as it is
            restoring = null;
            return !scars.isEmpty();
        }
        if (occupied(next)) {
            // standing in it: move one cell toward the way out
            BlockPos toward = order.size() > 1 ? order.get(1) : exit;
            if (toward != null && restoreTicks % 10 == 1) head(level, toward);
            return true;
        }
        if (bubie.canReach(next)) {
            bubie.getNavigation().stop();
            bubie.lookAtBlock(next);
            if (restoreTicks % 8 != 0) return true; // a short pause per block, like any other placing
            ItemStack block = original(scars.get(next));
            if (block.isEmpty()) block = bubie.takeOne(Job::isFiller);
            if (block.isEmpty()) {
                scars.clear(); // nothing left to fill with
                return false;
            }
            if (BlockBreaker.place(bubie, next, block)) {
                bubie.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
                scars.remove(next);
                restoring = null;
            }
            return true;
        }
        if (restoreTicks % 10 == 1) head(level, next);
        return true;
    }

    /** The very block that was there, when it carries one (dirt gives dirt; stone gives cobblestone, so filler). */
    private ItemStack original(BlockState was) {
        net.minecraft.world.item.Item item = was.getBlock().asItem();
        if (item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        return bubie.takeOne(s -> s.is(item));
    }
}
