package com.bubie.job;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Quarry: digs out everything natural in the area, layer by layer, leaving a staircase that winds down the walls so
 * the Bubie (and you) can walk out. Water and lava next to a block are plugged first with cobblestone it keeps.
 */
public class QuarryJob extends Job {
    private final boolean stairs;
    private final int perimeter;

    public QuarryJob(BubieEntity bubie, WorkArea area) {
        super(bubie, area);
        stairs = area.sizeX() >= 3 && area.sizeZ() >= 3 && area.sizeY() >= 2;
        perimeter = 2 * (area.sizeX() + area.sizeZ()) - 4;
    }

    @Override
    public boolean layered() {
        return true;
    }

    @Override
    public boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) return false;
        return !isProtected(pos);
    }

    /** One step per layer, each next to the one above it, walking round the edge of the area. */
    @Override
    public boolean isProtected(BlockPos pos) {
        if (!stairs) return false;
        int layer = area.max().getY() - pos.getY();
        int[] cell = perimeterCell(Math.floorMod(layer, perimeter));
        return pos.getX() == area.min().getX() + cell[0] && pos.getZ() == area.min().getZ() + cell[1];
    }

    private int[] perimeterCell(int p) {
        int w = area.sizeX(), d = area.sizeZ();
        if (p < w) return new int[]{p, 0};
        p -= w;
        if (p < d - 1) return new int[]{w - 1, p + 1};
        p -= d - 1;
        if (p < w - 1) return new int[]{w - 2 - p, d - 1};
        p -= w - 1;
        return new int[]{0, d - 2 - p};
    }

    @Override
    public int keep(ItemStack stack) {
        if (stack.is(Items.TORCH)) return 64;
        return isFiller(stack) ? 64 : 0;
    }
}
