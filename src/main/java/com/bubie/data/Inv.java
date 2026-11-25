package com.bubie.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import javax.annotation.Nullable;

/**
 * Stack-style helpers over NeoForge's transfer API (resource handlers and transactions), so the jobs keep thinking in
 * "insert this stack, get the rest back" as they did with item handlers.
 */
public final class Inv {
    private Inv() {
    }

    @Nullable
    public static ResourceHandler<ItemResource> at(Level level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(Capabilities.Item.BLOCK, pos, side);
    }

    public static ItemStack get(ResourceHandler<ItemResource> handler, int index) {
        return ItemUtil.getStack(handler, index);
    }

    /** Inserts wherever the handler likes (existing stacks first); returns what did not fit. */
    public static ItemStack insert(ResourceHandler<ItemResource> handler, ItemStack stack, boolean simulate) {
        return ItemUtil.insertItemReturnRemaining(handler, stack, simulate, null);
    }

    public static ItemStack insert(ResourceHandler<ItemResource> handler, int index, ItemStack stack, boolean simulate) {
        return ItemUtil.insertItemReturnRemaining(handler, index, stack, simulate, null);
    }

    /** Takes up to {@code amount} from one slot; returns what was (or would be) taken. */
    public static ItemStack extract(ResourceHandler<ItemResource> handler, int index, int amount, boolean simulate) {
        ItemResource resource = handler.getResource(index);
        if (resource.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        try (Transaction tx = Transaction.openRoot()) {
            int got = handler.extract(index, resource, amount, tx);
            if (!simulate) tx.commit();
            return got <= 0 ? ItemStack.EMPTY : resource.toStack(got);
        }
    }

    // ---------------------------------------------------------------- energy (Forge Energy)

    /** The FE battery of an item (batteries, electric tools), changed in place; null if it has none. */
    @Nullable
    public static EnergyHandler energy(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return stack.getCapability(Capabilities.Energy.ITEM, ItemAccess.forStack(stack));
    }

    public static int extractEnergy(EnergyHandler handler, int amount, boolean simulate) {
        if (amount <= 0) return 0;
        try (Transaction tx = Transaction.openRoot()) {
            int got = handler.extract(amount, tx);
            if (!simulate) tx.commit();
            return got;
        }
    }

    public static int insertEnergy(EnergyHandler handler, int amount, boolean simulate) {
        if (amount <= 0) return 0;
        try (Transaction tx = Transaction.openRoot()) {
            int got = handler.insert(amount, tx);
            if (!simulate) tx.commit();
            return got;
        }
    }

    /** It gives energy out (a charged battery; tools that only take energy in do not). */
    public static boolean canGive(@Nullable EnergyHandler handler) {
        return handler != null && extractEnergy(handler, 1, true) > 0;
    }
}
