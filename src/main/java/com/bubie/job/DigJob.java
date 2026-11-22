package com.bubie.job;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.DigFilter;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

/** Digger: sand, gravel, dirt and clay in the area, layer by layer from the top (filters in the menu). */
public class DigJob extends Job {
    private final int filter;

    public DigJob(BubieEntity bubie, WorkArea area, int filter) {
        super(bubie, area);
        this.filter = filter;
    }

    @Override
    public boolean layered() {
        return true;
    }

    private boolean on(DigFilter f) {
        return (filter & f.bit()) != 0;
    }

    @Override
    public boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BrushableBlock) return false; // suspicious sand/gravel: archaeology
        if (!state.getFluidState().isEmpty()) return false;
        if (on(DigFilter.SAND) && (state.is(BlockTags.SAND) || state.is(Tags.Blocks.SAND))) return true;
        if (on(DigFilter.GRAVEL) && (state.is(Blocks.GRAVEL) || state.is(Tags.Blocks.GRAVEL))) return true;
        if (on(DigFilter.DIRT) && (state.is(BlockTags.DIRT) || state.is(Blocks.DIRT_PATH))) return true;
        return on(DigFilter.CLAY) && state.is(Blocks.CLAY);
    }

    @Override
    public int keep(ItemStack stack) {
        return isFiller(stack) ? 32 : 0;
    }
}
