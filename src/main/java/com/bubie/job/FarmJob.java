package com.bubie.job;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.Status;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Farmer: harvests ripe crops and plants them again with their own seeds; picks sweet berries without breaking the
 * bush; cuts sugar cane, cactus and bamboo above the bottom block; takes pumpkins and melons off their stems.
 */
public class FarmJob extends Job {
    public FarmJob(BubieEntity bubie, WorkArea area) {
        super(bubie, area);
    }

    @Override
    public boolean plugsFluids() {
        return false; // farms need their water
    }

    @Override
    public boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state);
        if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= 3;
        if (block instanceof CocoaBlock) return state.getValue(CocoaBlock.AGE) >= 2;
        if (block instanceof SweetBerryBushBlock) return state.getValue(SweetBerryBushBlock.AGE) >= 3;
        if (state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS) || state.is(Blocks.BAMBOO)) {
            return level.getBlockState(pos.below()).is(block) && !level.getBlockState(pos.below(2)).is(block);
        }
        if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockState side = level.getBlockState(pos.relative(dir));
                if (side.getBlock() instanceof AttachedStemBlock && side.getValue(AttachedStemBlock.FACING) == dir.getOpposite()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isHarvest(BlockState state) {
        return state.getBlock() instanceof SweetBerryBushBlock;
    }

    @Override
    public boolean perform(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        Block block = state.getBlock();
        // Crops some mods harvest with a right click, keeping the plant (Farmer's Delight tomatoes and the like).
        if (block instanceof CropBlock && BlockBreaker.useBlock(bubie, pos) && level.getBlockState(pos) != state) return true;
        if (!super.perform(level, pos, state, tool)) return false;
        BlockState replant = null;
        if (block instanceof CropBlock crop) replant = crop.getStateForAge(0);
        else if (block instanceof NetherWartBlock) replant = block.defaultBlockState();
        else if (block instanceof CocoaBlock) replant = state.setValue(CocoaBlock.AGE, 0);
        if (replant == null || !level.getBlockState(pos).isAir()) return true;

        // the seed the crop itself names (its pick-block item), else whatever item places this block
        Item fromPick = block.getCloneItemStack(level, pos, state).getItem();
        Item seed = fromPick != net.minecraft.world.item.Items.AIR ? fromPick : block.asItem();
        if (seed == net.minecraft.world.item.Items.AIR) return true;
        ItemStack one = bubie.takeOne(s -> s.is(seed));
        if (one.isEmpty()) {
            bubie.setStatus(Status.NO_SEEDS);
            return true;
        }
        if (replant.canSurvive(level, pos)) {
            level.setBlockAndUpdate(pos, replant);
        } else {
            bubie.insert(one);
        }
        return true;
    }

    /** Seeds and seed-like crops (carrots, potatoes) stay with the Bubie for replanting. */
    @Override
    public int keep(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item) {
            Block block = item.getBlock();
            if (block instanceof CropBlock || block instanceof StemBlock || block instanceof NetherWartBlock
                    || block instanceof CocoaBlock) {
                return 16;
            }
        }
        return 0;
    }
}
