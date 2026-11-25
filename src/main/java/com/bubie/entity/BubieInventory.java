package com.bubie.entity;

import com.bubie.Bubie;
import com.bubie.item.ChipItem;
import com.bubie.item.ChipType;
import com.bubie.item.DriveItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A Bubie's containers (storage backed by its Drive, fuel, upgrade chips and DJ disc) and the Drive/Chip sync rules.
 */
final class BubieInventory {
    private static final ResourceLocation SPEED_CHIP_ID = Bubie.id("speed_chip");

    private final BubieEntity bubie;
    /** What it carries. While a drive is in, every change is written onto the drive too (see {@link #flushDrive}). */
    private final SimpleContainer storage = new SimpleContainer(BubieEntity.STORAGE_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            flushDrive();
        }
    };
    /** The drive slot: a Hard Drive (18) or a Solid-State Drive (36), or nothing (one slot, item by item). */
    private final SimpleContainer drive = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            onDriveChanged();
        }
    };
    private final SimpleContainer extra = new SimpleContainer(1 + BubieEntity.CHIP_SLOTS);
    /** The DJ's music disc, in a slot of its own: never stored away in a chest, never taking a storage slot. */
    private final SimpleContainer disc = new SimpleContainer(1);
    private final Map<ChipType, Integer> chips = new EnumMap<>(ChipType.class);

    /** The drive whose contents are in storage now (by identity: a drive taken out and put back is a new stack). */
    private ItemStack loadedDrive = ItemStack.EMPTY;
    /** Whether a drive was in (a drive taken out leaves its old stack in the slot, shrunk to nothing). */
    private boolean hadDrive;
    private boolean loading;

    BubieInventory(BubieEntity bubie) {
        this.bubie = bubie;
        extra.addListener(container -> onChipsChanged());
    }

    /** Every Bubie comes with a Hard Drive (its recipe takes one). */
    void initDefaultDrive() {
        loading = true;
        try {
            drive.setItem(0, new ItemStack(Bubie.HARD_DRIVE.get()));
            loadedDrive = drive.getItem(0);
            hadDrive = true;
        } finally {
            loading = false;
        }
    }

    SimpleContainer storage() {
        return storage;
    }

    SimpleContainer extra() {
        return extra;
    }

    SimpleContainer disc() {
        return disc;
    }

    SimpleContainer drive() {
        return drive;
    }

    int chipCount(ChipType type) {
        return chips.getOrDefault(type, 0);
    }

    boolean installChip(ItemStack stack) {
        for (int i = 1; i <= BubieEntity.CHIP_SLOTS; i++) {
            if (extra.getItem(i).isEmpty()) {
                extra.setItem(i, stack.split(1));
                return true;
            }
        }
        return false;
    }

    int count(Item item) {
        int n = 0;
        for (int i = 0; i < BubieEntity.STORAGE_SLOTS; i++) {
            if (storage.getItem(i).is(item)) n += storage.getItem(i).getCount();
        }
        return n;
    }

    /** Puts a stack into the open storage slots; returns what did not fit. */
    ItemStack insert(ItemStack stack) {
        ItemStack rest = stack.copy();
        int unlocked = bubie.getUnlockedSlots();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int i = 0; i < unlocked && !rest.isEmpty(); i++) {
                ItemStack in = storage.getItem(i);
                if (pass == 0 && !in.isEmpty() && ItemStack.isSameItemSameComponents(in, rest)) {
                    int move = Math.min(rest.getCount(), in.getMaxStackSize() - in.getCount());
                    if (move > 0) {
                        in.grow(move);
                        rest.shrink(move);
                    }
                } else if (pass == 1 && in.isEmpty()) {
                    storage.setItem(i, rest);
                    rest = ItemStack.EMPTY;
                }
            }
        }
        storage.setChanged();
        return rest;
    }

    boolean hasFreeSlot() {
        for (int i = 0; i < bubie.getUnlockedSlots(); i++) {
            if (storage.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    boolean hasStoredItems() {
        for (int i = 0; i < bubie.getUnlockedSlots(); i++) {
            if (!storage.getItem(i).isEmpty()) return true;
        }
        return false;
    }

    /** Takes one item matching the test from storage, or EMPTY. */
    ItemStack takeOne(Predicate<ItemStack> test) {
        for (int i = 0; i < BubieEntity.STORAGE_SLOTS; i++) {
            ItemStack in = storage.getItem(i);
            if (!in.isEmpty() && test.test(in)) {
                ItemStack one = in.split(1);
                storage.setChanged();
                return one;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Storage slots it has: its drive's capacity, or one without a drive. */
    private int capacity() {
        int cap = DriveItem.capacity(drive.getItem(0));
        return cap > 0 ? Math.min(BubieEntity.STORAGE_SLOTS, cap) : 1;
    }

    /** Storage onto the drive, so the drive always holds what the Bubie carries. */
    private void flushDrive() {
        if (loading || bubie.level().isClientSide) return;
        ItemStack d = drive.getItem(0);
        if (!d.isEmpty() && d.getItem() instanceof DriveItem) DriveItem.store(d, storage);
    }

    /**
     * A drive went in or out. One taken out already holds the cargo (every change was written onto it), so storage
     * empties; one put in fills storage with what is on it. What it carried with no drive at all joins the new drive.
     */
    private void onDriveChanged() {
        if (loading || bubie.level().isClientSide) return;
        ItemStack now = drive.getItem(0);
        boolean has = !now.isEmpty() && now.getItem() instanceof DriveItem;
        if (now == loadedDrive && has == hadDrive) return;
        List<ItemStack> loose = new ArrayList<>();
        if (!hadDrive) {
            for (int i = 0; i < BubieEntity.STORAGE_SLOTS; i++) {
                if (!storage.getItem(i).isEmpty()) loose.add(storage.getItem(i).copy());
            }
        }
        loading = true;
        try {
            storage.clearContent();
            if (now.getItem() instanceof DriveItem) {
                var on = DriveItem.contents(now);
                for (int i = 0; i < on.size() && i < BubieEntity.STORAGE_SLOTS; i++) storage.setItem(i, on.get(i).copy());
            }
            loadedDrive = now;
            hadDrive = has;
        } finally {
            loading = false;
        }
        bubie.setUnlockedSlots(capacity());
        for (ItemStack stack : loose) {
            ItemStack rest = insert(stack);
            if (!rest.isEmpty()) bubie.spawnAtLocation(rest);
        }
        flushDrive();
        bubie.runner().reset();
    }

    void onChipsChanged() {
        if (loading) return;
        chips.clear();
        for (int i = 1; i <= BubieEntity.CHIP_SLOTS; i++) {
            if (extra.getItem(i).getItem() instanceof ChipItem chip) chips.merge(chip.type(), 1, Integer::sum);
        }
        if (bubie.level().isClientSide) return;
        int unlocked = capacity();
        bubie.setUnlockedSlots(unlocked);
        // Items left in slots that just got locked move to open slots, or drop.
        for (int i = unlocked; i < BubieEntity.STORAGE_SLOTS; i++) {
            ItemStack stack = storage.removeItemNoUpdate(i);
            if (stack.isEmpty()) continue;
            ItemStack rest = insert(stack);
            if (!rest.isEmpty()) bubie.spawnAtLocation(rest);
        }
        var speed = bubie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_CHIP_ID);
            int n = chipCount(ChipType.SPEED);
            if (n > 0) speed.addOrUpdateTransientModifier(
                    new AttributeModifier(SPEED_CHIP_ID, 0.2 * n, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    void save(CompoundTag tag) {
        tag.put("Storage", saveContainer(storage));
        tag.put("Extra", saveContainer(extra));
        tag.put("Disc", saveContainer(disc));
        tag.put("Drive", saveContainer(drive));
    }

    void load(CompoundTag tag) {
        loading = true;
        try {
            loadContainer(storage, tag.getList("Storage", Tag.TAG_COMPOUND));
            loadContainer(extra, tag.getList("Extra", Tag.TAG_COMPOUND));
            loadContainer(disc, tag.getList("Disc", Tag.TAG_COMPOUND));
            if (tag.contains("Drive")) {
                loadContainer(drive, tag.getList("Drive", Tag.TAG_COMPOUND));
            } else {
                // a Bubie from before drives: a Hard Drive, or a Solid-State Drive if it had Storage Chips (they go)
                int storageChips = 0;
                for (int i = 1; i <= BubieEntity.CHIP_SLOTS; i++) {
                    if (extra.getItem(i).getItem() instanceof ChipItem chip && chip.type() == ChipType.STORAGE) {
                        extra.setItem(i, ItemStack.EMPTY);
                        storageChips++;
                    }
                }
                drive.setItem(0, new ItemStack(storageChips > 0 ? Bubie.SOLID_STATE_DRIVE.get() : Bubie.HARD_DRIVE.get()));
            }
            loadedDrive = drive.getItem(0);
            hadDrive = !loadedDrive.isEmpty() && loadedDrive.getItem() instanceof DriveItem;
            if (!tag.contains("Disc")) {
                // a Bubie from before the disc slot: its first disc in storage moves there
                for (int i = 0; i < storage.getContainerSize() && disc.isEmpty(); i++) {
                    if (BubieEntity.isDisc(storage.getItem(i))) disc.setItem(0, storage.removeItem(i, 1));
                }
            }
        } finally {
            loading = false;
        }
        onChipsChanged();
        flushDrive(); // a Bubie from before drives: its cargo goes onto its new drive
    }

    /** Keeps slot positions (SimpleContainer's own format packs items together, which would mix fuel and chips). */
    private ListTag saveContainer(SimpleContainer container) {
        ListTag list = new ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) i);
            list.add(stack.save(bubie.registryAccess(), entry));
        }
        return list;
    }

    private void loadContainer(SimpleContainer container, ListTag list) {
        container.clearContent();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot < container.getContainerSize()) {
                container.setItem(slot, ItemStack.parseOptional(bubie.registryAccess(), entry));
            }
        }
    }
}
