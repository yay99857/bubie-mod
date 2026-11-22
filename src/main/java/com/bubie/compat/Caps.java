package com.bubie.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

/** Forge capabilities as plain nullable lookups, the way the rest of the mod asks for them. */
public final class Caps {
    private Caps() {
    }

    /** The inventory of the block at pos, seen from that side (null: no particular side). */
    @Nullable
    public static IItemHandler items(Level level, BlockPos pos, @Nullable Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null);
    }

    /** The battery of an item that holds Forge Energy. */
    @Nullable
    public static IEnergyStorage energy(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null);
    }
}
