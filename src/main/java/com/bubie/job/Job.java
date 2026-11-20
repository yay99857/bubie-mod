package com.bubie.job;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** One kind of work. The {@link JobRunner} finds targets (with the job's rules), walks there and calls {@link #perform}. */
public abstract class Job {
    protected final BubieEntity bubie;
    protected final WorkArea area;

    protected Job(BubieEntity bubie, WorkArea area) {
        this.bubie = bubie;
        this.area = area;
    }

    /** Whether this block, inside the area, is work for this job. */
    public abstract boolean isTarget(ServerLevel level, BlockPos pos, BlockState state);

    /** Layered jobs clear the area from the top down, one layer at a time, and are DONE at the bottom. */
    public boolean layered() {
        return false;
    }

    /** Blocks this job must leave in place (the quarry's stairs). */
    public boolean isProtected(BlockPos pos) {
        return false;
    }

    /** Whether the job walks to loose items in the area and picks them up between blocks. */
    public boolean collectsItems() {
        return true;
    }

    /** How much longer than one block this target takes (a whole tree counts its logs). */
    public float effort(ServerLevel level, BlockPos pos) {
        return 1.0F;
    }

    /** Jobs that place blocks (the builder) instead of breaking them. */
    public boolean placesBlocks() {
        return false;
    }

    /** Finished for good (the area dug out, the tunnel done, the base built). */
    public boolean isDone() {
        return false;
    }

    /** The item needed to work on this target, which the Bubie fetches from its chest if it has none. */
    @Nullable
    public net.minecraft.world.item.Item requiredItem(BlockPos pos) {
        return null;
    }

    /** Everything the job still needs, by item, for one trip to the chest. */
    public java.util.Map<net.minecraft.world.item.Item, Integer> shoppingList() {
        return java.util.Map.of();
    }

    /** Whether to plug water and lava next to a block before breaking it (not for farms and trees). */
    public boolean plugsFluids() {
        return true;
    }

    /** Right-click instead of breaking (berry bushes): no tool, no fluid checks. */
    public boolean isHarvest(BlockState state) {
        return false;
    }

    /**
     * Next block to work on. Most jobs scan the area; the tunnel miner follows a plan.
     * Returns null while still scanning, or {@link JobRunner#NOTHING} when there is nothing to do right now.
     */
    @Nullable
    public BlockPos next(JobRunner runner) {
        return runner.scanNext();
    }

    /** Does the work on a reached target; false if it was refused (the target is skipped for a while). */
    public boolean perform(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        return isHarvest(state) ? BlockBreaker.useBlock(bubie, pos) : BlockBreaker.breakBlock(bubie, pos, tool);
    }

    /** How many of this item to keep when unloading at the chest (seeds, saplings, cobblestone for lava). */
    public int keep(ItemStack stack) {
        return 0;
    }

    // ------------------------------------------------------------- shared rules

    /** Cobblestone and friends: what the Bubie uses to plug water and lava before it digs next to them. */
    public static boolean isFiller(ItemStack stack) {
        return stack.is(net.neoforged.neoforge.common.Tags.Items.COBBLESTONES)
                || stack.is(net.minecraft.world.item.Items.DIRT)
                || stack.is(net.minecraft.world.item.Items.NETHERRACK)
                || stack.is(net.minecraft.world.item.Items.COBBLED_DEEPSLATE)
                || stack.is(net.minecraft.world.item.Items.STONE);
    }
}
