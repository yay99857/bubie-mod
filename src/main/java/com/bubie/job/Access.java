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
    /** Terrain changes it may make for one trip, however well it goes. */
    static final int MAX_STEPS = 256;
    /** Never drops into a hole deeper than this when digging under its feet. */
    private static final int MAX_DROP = 4;

    private final BubieEntity bubie;
    /** Blocks broken to get somewhere that the job would not have broken, and what they were. */
    private final Map<BlockPos, BlockState> scars = new LinkedHashMap<>();
    @Nullable
    private BlockPos stepsFor;
    private int steps, lastStep;
    @Nullable
    private BlockPos restoring;
    private int restoreTicks;

    Access(BubieEntity bubie) {
        this.bubie = bubie;
    }

    void reset() {
        stepsFor = null;
        steps = 0;
        plan = null;
        restoring = null;
        restoreTicks = 0;
    }

    private String failure = "none";

    /** Why the last way it tried did not work out (for the log). */
    String lastFailure() {
        return failure;
    }

    /** It changed the terrain or stepped along its way just now. */
    boolean busy() {
        return bubie.tickCount - lastStep <= 60;
    }

    /** On a way it planned to this goal (not given up, not arrived). */
    boolean following(BlockPos goal) {
        return plan != null && goal.equals(stepsFor) && bubie.tickCount - lastStep <= 200;
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

    /** Cells searched at most when planning a way (a box a few blocks around the Bubie and its goal). */
    private static final int MAX_NODES = 6000;
    /** Extra cost of a block to break, of a floor to fill, of a step up or down (walking a block costs 1). */
    private static final double BREAK = 4, FILL = 6, CLIMB = 0.4;

    /** The cells its feet go through, from where it stood when planning to where it can do what it came for. */
    @Nullable
    private List<BlockPos> plan;
    private int planIndex, stall, replans;
    private static long lastPlanTick = Long.MIN_VALUE;

    /**
     * One move along a planned way to where it can reach {@code goal} (within {@code arrive} blocks of the goal's
     * centre, from its eyes when {@code eyes}, else from its feet): either a change to the terrain the way needs next
     * or a walk to the next cell. The way is planned once (a small A* that weighs walking against digging and filling)
     * and planned again if the world gets in the way. False when there is no way it can make: the caller gives up.
     */
    boolean step(ServerLevel level, BlockPos goal, Job job, double arrive, boolean eyes) {
        // a new trip (another goal, or the same one again after a while): plan from scratch
        if (!goal.equals(stepsFor) || bubie.tickCount - lastStep > 200) {
            stepsFor = goal.immutable();
            steps = 0;
            replans = 0;
            plan = null;
        }
        lastStep = bubie.tickCount;
        if (steps >= MAX_STEPS || replans > 8) {
            failure = steps >= MAX_STEPS ? "too many blocks to change" : "the way kept changing under it";
            return false;
        }
        BlockPos feet = bubie.blockPosition();
        if (plan == null) {
            // one plan per server tick, however many Bubies need one (a plan takes a millisecond or two)
            if (level.getGameTime() == lastPlanTick) return true;
            lastPlanTick = level.getGameTime();
            plan = findWay(level, feet, goal, arrive, eyes, job);
            planIndex = 0;
            stall = 0;
            if (plan == null) {
                failure = "no way within reach of digging or filling" + (hasFiller() ? "" : " (no filler)");
                return false;
            }
        }
        while (planIndex < plan.size() && feet.equals(plan.get(planIndex))) {
            planIndex++;
            stall = 0;
        }
        if (planIndex >= plan.size()) {
            plan = null; // at the end of it: the caller sees it can reach now
            return true;
        }
        BlockPos next = plan.get(planIndex);
        int across = Math.abs(feet.getX() - next.getX()) + Math.abs(feet.getZ() - next.getZ());
        if (across > 1 || Math.abs(feet.getY() - next.getY()) > 1) {
            plan = null; // pushed off its way
            replans++;
            return true;
        }
        Prep prep = prepare(level, feet, next, job);
        if (prep == Prep.CHANGED) {
            steps++;
            stall = 0;
            bubie.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
            bubie.spendEnergy(20);
            return true;
        }
        if (prep == Prep.BLOCKED || ++stall > 6) {
            failure = prep == Prep.BLOCKED ? "a block it could not open or fill at " + next.toShortString() : "could not step to " + next.toShortString();
            plan = null; // the world is not as planned (or it can't get there): plan again
            replans++;
            return true;
        }
        walk(next);
        return true;
    }

    private enum Prep { READY, CHANGED, BLOCKED }

    /** What still has to change before it can move from {@code feet} into {@code next}: one change at a time. */
    private Prep prepare(ServerLevel level, BlockPos feet, BlockPos next, Job job) {
        boolean down = next.equals(feet.below());
        for (BlockPos clear : clearFor(feet, next)) {
            if (passable(level, clear)) continue;
            if (down && !safeDrop(level, clear)) return Prep.BLOCKED;
            return dig(level, clear, job) ? Prep.CHANGED : Prep.BLOCKED;
        }
        if (down) return Prep.READY;
        BlockPos floor = next.below();
        if (sturdy(level, floor)) return Prep.READY;
        return fill(level, floor) ? Prep.CHANGED : Prep.BLOCKED;
    }

    /** The cells that must be open for the move: the one it moves into, and the corner it passes through. */
    private static List<BlockPos> clearFor(BlockPos feet, BlockPos next) {
        if (next.equals(feet.below())) return List.of(next);
        int dy = next.getY() - feet.getY();
        if (dy > 0) return List.of(feet.above(), next); // head room to step up, then the spot
        if (dy < 0) return List.of(next.above(), next); // through the spot at its level, then down into it
        return List.of(next);
    }

    private record Node(BlockPos pos, double cost, double estimate, @Nullable Node from) {
    }

    /** The cheapest way, as cells for its feet, to a cell from which it can reach the goal; null if none is near. */
    @Nullable
    private List<BlockPos> findWay(ServerLevel level, BlockPos start, BlockPos goal, double arrive, boolean eyes, Job job) {
        int minX = Math.min(start.getX(), goal.getX()) - 8, maxX = Math.max(start.getX(), goal.getX()) + 8;
        int minY = Math.min(start.getY(), goal.getY()) - 6, maxY = Math.max(start.getY(), goal.getY()) + 6;
        int minZ = Math.min(start.getZ(), goal.getZ()) - 8, maxZ = Math.max(start.getZ(), goal.getZ()) + 8;
        Vec3 target = Vec3.atCenterOf(goal);
        boolean filler = hasFiller();
        java.util.PriorityQueue<Node> open = new java.util.PriorityQueue<>(Comparator.comparingDouble(n -> n.cost() + n.estimate()));
        Map<BlockPos, Double> best = new java.util.HashMap<>();
        open.add(new Node(start, 0, 0, null));
        best.put(start, 0.0);
        int seen = 0;
        while (!open.isEmpty() && seen++ < MAX_NODES) {
            Node node = open.poll();
            BlockPos at = node.pos();
            if (node.cost() > best.getOrDefault(at, Double.MAX_VALUE)) continue;
            Vec3 from = eyes ? new Vec3(at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5) : Vec3.atBottomCenterOf(at);
            if (from.distanceTo(target) <= arrive) {
                List<BlockPos> way = new ArrayList<>();
                for (Node n = node; n != null && n.from() != null; n = n.from()) way.add(0, n.pos());
                return way;
            }
            List<BlockPos> moves = new ArrayList<>(13);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) moves.add(at.relative(dir).above(dy));
            }
            moves.add(at.below());
            for (BlockPos next : moves) {
                if (next.getX() < minX || next.getX() > maxX || next.getY() < minY || next.getY() > maxY
                        || next.getZ() < minZ || next.getZ() > maxZ) continue;
                double step = moveCost(level, at, next, job, filler);
                if (step < 0) continue;
                double cost = node.cost() + step;
                if (cost >= best.getOrDefault(next, Double.MAX_VALUE)) continue;
                best.put(next, cost);
                double left = Math.max(0, Vec3.atCenterOf(next).distanceTo(target) - arrive);
                open.add(new Node(next, cost, left, node));
            }
        }
        return null;
    }

    /** What moving from {@code at} into {@code next} costs (digging and filling included), or -1 if it can't. */
    private double moveCost(ServerLevel level, BlockPos at, BlockPos next, Job job, boolean filler) {
        boolean down = next.equals(at.below());
        double cost = next.getY() != at.getY() ? 1 + CLIMB : 1;
        for (BlockPos clear : clearFor(at, next)) {
            if (passable(level, clear)) continue;
            if (job.isProtected(clear) || !breakable(level, clear)) return -1;
            cost += BREAK;
        }
        if (down) return safeDrop(level, next) ? cost : -1;
        BlockPos floor = next.below();
        if (sturdy(level, floor)) return cost;
        if (!filler || !level.getBlockState(floor).canBeReplaced()) return -1;
        return cost + FILL;
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
        if (job.isProtected(pos) || !breakable(level, pos)) return false;
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
                || state.is(Blocks.SOUL_SOIL) || state.is(Tags.BlockItems.ORES.block());
        // not cobblestone: it is what players (and the Builder) build with
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
