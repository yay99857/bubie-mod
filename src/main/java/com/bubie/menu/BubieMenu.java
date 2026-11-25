package com.bubie.menu;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.item.ChipItem;
import com.bubie.item.DriveItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

import javax.annotation.Nullable;

/**
 * The Bubie's menu: 36 storage slots (as many open as its drive holds: 18 for a Hard Drive, 36 for a Solid-State
 * Drive, one without a drive), its tool, fuel, drive and four chip slots. The
 * settings panel on the left of the screen reads the Bubie's synced data and sends commands over the network.
 */
public class BubieMenu extends AbstractContainerMenu {
    public static final int TOOL = BubieEntity.STORAGE_SLOTS, FUEL = TOOL + 1, DRIVE = FUEL + 1, CHIPS = DRIVE + 1;
    /** Where the drive slot sits: between the fuel and the chips. */
    public static final int DRIVE_X = 48;
    public static final int DISC = CHIPS + BubieEntity.CHIP_SLOTS, PLAYER = DISC + 1, END = PLAYER + 36;
    /** The disc slot sits in the DJ tab of the panel and only shows (on the client) while that tab is open. */
    public static final int DISC_X = 12, DISC_Y = 101;
    public static boolean djTabOpen;
    /** Slot layout, shared with the screen. */
    public static final int RIGHT = 122, GRID_X = RIGHT + 6, GRID_Y = 18, EQUIP_Y = 102, INV_Y = 146;

    @Nullable
    private final BubieEntity bubie;

    public BubieMenu(int id, Inventory inventory, BubieEntity bubie) {
        this(id, inventory, bubie, bubie.storage(), new ToolContainer(bubie), bubie.extra(), bubie.disc(), bubie.drive());
    }

    public static BubieMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        BubieEntity bubie = inventory.player.level().getEntity(entityId) instanceof BubieEntity b ? b : null;
        return new BubieMenu(id, inventory, bubie, new SimpleContainer(BubieEntity.STORAGE_SLOTS),
                new SimpleContainer(1), new SimpleContainer(1 + BubieEntity.CHIP_SLOTS), new SimpleContainer(1), new SimpleContainer(1));
    }

    private BubieMenu(int id, Inventory inventory, @Nullable BubieEntity bubie, Container storage, Container tool, Container extra,
                      Container disc, Container drive) {
        super(Bubie.BUBIE_MENU.get(), id);
        this.bubie = bubie;
        for (int i = 0; i < BubieEntity.STORAGE_SLOTS; i++) {
            final int index = i;
            addSlot(new Slot(storage, i, GRID_X + (i % 9) * 18, GRID_Y + (i / 9) * 18) {
                @Override
                public boolean isActive() {
                    return index < openSlots();
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return isActive();
                }
            });
        }
        addSlot(new FixedSlot(tool, 0, GRID_X, EQUIP_Y) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new FixedSlot(extra, BubieEntity.FUEL_SLOT, GRID_X + 24, EQUIP_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BubieEntity.isFuel(stack);
            }
        });
        addSlot(new FixedSlot(drive, 0, GRID_X + DRIVE_X, EQUIP_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DriveItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int i = 0; i < BubieEntity.CHIP_SLOTS; i++) {
            addSlot(new FixedSlot(extra, 1 + i, GRID_X + 72 + i * 18, EQUIP_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // the Storage Chip does nothing in a Bubie any more: it goes into a Solid-State Drive
                    return stack.getItem() instanceof ChipItem chip && chip.type() != com.bubie.item.ChipType.STORAGE;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        boolean client = inventory.player.level().isClientSide();
        addSlot(new FixedSlot(disc, 0, DISC_X, DISC_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BubieEntity.isDisc(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean isActive() {
                return !client || djTabOpen;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, GRID_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, GRID_X + col * 18, INV_Y + 58));
    }

    @Nullable
    public BubieEntity bubie() {
        return bubie;
    }

    public int openSlots() {
        return bubie == null ? BubieEntity.BASE_STORAGE : bubie.getUnlockedSlots();
    }

    @Override
    public boolean stillValid(Player player) {
        return bubie != null && bubie.isAlive() && bubie.distanceToSqr(player) < 10 * 10 && bubie.canCommand(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < PLAYER) {
            if (!moveItemStackTo(stack, PLAYER, END, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            if (stack.getItem() instanceof DriveItem && !slots.get(DRIVE).hasItem()) moved = moveItemStackTo(stack, DRIVE, DRIVE + 1, false);
            if (!moved && slots.get(CHIPS).mayPlace(stack)) moved = moveItemStackTo(stack, CHIPS, DISC, false);
            if (!moved && BubieEntity.isDisc(stack)) moved = moveItemStackTo(stack, DISC, PLAYER, false);
            if (!moved && slots.get(FUEL).mayPlace(stack)) moved = moveItemStackTo(stack, FUEL, FUEL + 1, false);
            if (!moved && stack.isDamageableItem() && !slots.get(TOOL).hasItem()) moved = moveItemStackTo(stack, TOOL, TOOL + 1, false);
            if (!moved) moved = moveItemStackTo(stack, 0, openSlots(), false);
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    /** The tool slot is the Bubie's main hand, so the tool shows in its hand. */
    private record ToolContainer(BubieEntity bubie) implements Container {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return bubie.getMainHandItem().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return bubie.getMainHandItem();
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack out = bubie.getMainHandItem().split(amount);
            if (bubie.getMainHandItem().isEmpty()) bubie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack out = bubie.getMainHandItem();
            bubie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            return out;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            bubie.setItemSlot(EquipmentSlot.MAINHAND, stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return bubie.isAlive();
        }

        @Override
        public void clearContent() {
            bubie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }
}
