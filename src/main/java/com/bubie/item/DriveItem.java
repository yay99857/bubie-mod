package com.bubie.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * A Bubie's storage: the Hard Drive (18 slots, every Bubie comes with one) or the Solid-State Drive (36). What the
 * Bubie carries lives on the drive (as a shulker box keeps its contents), so a drive taken out takes the cargo with it
 * and can go into another Bubie. An empty drive has no contents component at all.
 */
public class DriveItem extends Item {
    private final int capacity;

    public DriveItem(int capacity, Properties properties) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public static int capacity(ItemStack stack) {
        return stack.getItem() instanceof DriveItem drive ? drive.capacity : 0;
    }

    /** Writes the first {@link #capacity} slots of a container onto the drive (removing the component when empty). */
    public static void store(ItemStack drive, Container from) {
        int size = Math.min(capacity(drive), from.getContainerSize());
        List<ItemStack> items = new ArrayList<>(size);
        boolean any = false;
        for (int i = 0; i < size; i++) {
            ItemStack s = from.getItem(i);
            items.add(s.copy());
            any |= !s.isEmpty();
        }
        if (any) drive.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        else drive.remove(DataComponents.CONTAINER);
    }

    /** What is on the drive, slot by slot ({@link #capacity} long). */
    public static NonNullList<ItemStack> contents(ItemStack drive) {
        NonNullList<ItemStack> list = NonNullList.withSize(capacity(drive), ItemStack.EMPTY);
        ItemContainerContents contents = drive.get(DataComponents.CONTAINER);
        if (contents != null) contents.copyInto(list);
        return list;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.bubie.drive.capacity", capacity).withStyle(ChatFormatting.GRAY));
        List<ItemStack> held = new ArrayList<>();
        for (ItemStack s : contents(stack)) if (!s.isEmpty()) held.add(s);
        if (held.isEmpty()) {
            tooltip.accept(Component.translatable("item.bubie.drive.empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        int shown = 0;
        for (ItemStack s : held) {
            if (shown++ == 5) {
                tooltip.accept(Component.translatable("item.bubie.drive.more", held.size() - 5).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            tooltip.accept(Component.literal(s.getCount() + " × ").append(s.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }
}
