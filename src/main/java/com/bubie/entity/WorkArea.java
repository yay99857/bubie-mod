package com.bubie.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

/** A box of blocks, both corners inclusive. */
public record WorkArea(BlockPos min, BlockPos max) {
    public static WorkArea of(BlockPos a, BlockPos b) {
        return new WorkArea(
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
    }

    public static WorkArea around(BlockPos center, int radius, int down, int up) {
        return of(center.offset(-radius, -down, -radius), center.offset(radius, up, radius));
    }

    public int sizeX() {
        return max.getX() - min.getX() + 1;
    }

    public int sizeY() {
        return max.getY() - min.getY() + 1;
    }

    public int sizeZ() {
        return max.getZ() - min.getZ() + 1;
    }

    public int largestSide() {
        return Math.max(sizeX(), Math.max(sizeY(), sizeZ()));
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public AABB box() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    public BlockPos center() {
        return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Min", min.asLong());
        tag.putLong("Max", max.asLong());
        return tag;
    }

    public static WorkArea load(CompoundTag tag) {
        return of(BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")));
    }

    public String describe() {
        return sizeX() + "×" + sizeY() + "×" + sizeZ();
    }
}
