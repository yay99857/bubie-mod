package com.bubie.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A Bubie's storage: the Hard Drive (18 slots, every Bubie comes with one) or the Solid-State Drive (36). What the
 * Bubie carries lives on the drive (as a shulker box keeps its contents), so a drive taken out takes the cargo with it
 * and can go into another Bubie. An empty drive has no contents tag at all.
 */
public class DriveItem extends Item {
    private static final String KEY = "Items";
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

    public static boolean hasContents(ItemStack drive) {
        CompoundTag tag = drive.getTag();
        return tag != null && tag.contains(KEY, Tag.TAG_LIST) && !tag.getList(KEY, Tag.TAG_COMPOUND).isEmpty();
    }

    /** Writes the first {@link #capacity} slots of a container onto the drive (removing the tag when empty). */
    public static void store(ItemStack drive, Container from) {
        int size = Math.min(capacity(drive), from.getContainerSize());
        ListTag list = new ListTag();
        for (int i = 0; i < size; i++) {
            ItemStack s = from.getItem(i);
            if (s.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) i);
            list.add(s.save(entry));
        }
        if (!list.isEmpty()) {
            drive.getOrCreateTag().put(KEY, list);
        } else {
            CompoundTag tag = drive.getTag();
            if (tag != null) {
                tag.remove(KEY);
                if (tag.isEmpty()) drive.setTag(null);
            }
        }
    }

    /** What is on the drive, slot by slot ({@link #capacity} long). */
    public static NonNullList<ItemStack> contents(ItemStack drive) {
        NonNullList<ItemStack> list = NonNullList.withSize(capacity(drive), ItemStack.EMPTY);
        CompoundTag tag = drive.getTag();
        if (tag == null || !tag.contains(KEY, Tag.TAG_LIST)) return list;
        ListTag items = tag.getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot < list.size()) list.set(slot, ItemStack.of(entry));
        }
        return list;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.bubie.drive.capacity", capacity).withStyle(ChatFormatting.GRAY));
        List<ItemStack> held = new ArrayList<>();
        for (ItemStack s : contents(stack)) if (!s.isEmpty()) held.add(s);
        if (held.isEmpty()) {
            tooltip.add(Component.translatable("item.bubie.drive.empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        int shown = 0;
        for (ItemStack s : held) {
            if (shown++ == 5) {
                tooltip.add(Component.translatable("item.bubie.drive.more", held.size() - 5).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            tooltip.add(Component.literal(s.getCount() + " × ").append(s.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }
}
