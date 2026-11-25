package com.bubie.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

import javax.annotation.Nullable;

/**
 * A slot that inventory-sorting mods leave alone: the Bubie's tool, fuel and chips, a station's fuel.
 * <p>
 * Inventory Tweaks Refoxed sorts every slot of an open menu that is not the player's, as one chest, unless the slot is
 * a plain slot over an empty container (and, from its newer versions, an item-handler slot that cannot be written to).
 * So this slot sits on an empty container of its own and reads and writes its real inventory itself: clicking,
 * shift-clicking, hoppers and the screen see the items as before, while sorters skip it.
 */
public class FixedSlot extends Slot {
    @Nullable
    private final ItemStacksResourceHandler handler;
    @Nullable
    private final Container backing;

    public FixedSlot(ItemStacksResourceHandler handler, int index, int x, int y) {
        super(new SimpleContainer(0), index, x, y);
        this.handler = handler;
        this.backing = null;
    }

    public FixedSlot(Container container, int index, int x, int y) {
        super(new SimpleContainer(0), index, x, y);
        this.handler = null;
        this.backing = container;
    }

    @Override
    public ItemStack getItem() {
        int slot = getContainerSlot();
        return handler != null ? com.bubie.data.Inv.get(handler, slot) : backing.getItem(slot);
    }

    @Override
    public void set(ItemStack stack) {
        int slot = getContainerSlot();
        if (handler != null) handler.set(slot, ItemResource.of(stack), stack.getCount());
        else backing.setItem(slot, stack);
        setChanged();
    }

    @Override
    public ItemStack remove(int amount) {
        int slot = getContainerSlot();
        if (handler != null) {
            ItemStack stack = getItem();
            ItemStack taken = stack.split(amount);
            set(stack);
            return taken;
        }
        ItemStack taken = backing.removeItem(slot, amount);
        setChanged();
        return taken;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        int slot = getContainerSlot();
        return handler != null ? handler.isValid(slot, ItemResource.of(stack)) : backing.canPlaceItem(slot, stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return !getItem().isEmpty();
    }

    @Override
    public int getMaxStackSize() {
        return handler != null ? 99 : backing.getMaxStackSize();
    }

    @Override
    public void setChanged() {
        if (backing != null) backing.setChanged();
    }
}
