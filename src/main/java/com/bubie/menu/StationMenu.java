package com.bubie.menu;

import com.bubie.Bubie;
import com.bubie.block.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class StationMenu extends AbstractContainerMenu {
    public static final int FUEL_X = 8, FUEL_Y = 92, INV_Y = 122;
    private static final int STORAGE = StationBlockEntity.STORAGE, FUEL = STORAGE, PLAYER = STORAGE + 1, END = PLAYER + 36;

    private final ContainerData data;
    private final BlockPos pos;
    private final Player player;
    private int ticks;
    private java.util.List<com.bubie.network.StationBubiesPayload.Entry> sent;

    public StationMenu(int id, Inventory inventory, StationBlockEntity station, ContainerData data) {
        this(id, inventory, station.items(), data, station.getBlockPos());
    }

    public static StationMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new StationMenu(id, inventory, new ItemStackHandler(STORAGE + 1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot != FUEL || StationBlockEntity.isStationFuel(stack);
            }
        }, new SimpleContainerData(4), pos);
    }

    private StationMenu(int id, Inventory inventory, IItemHandler items, ContainerData data, BlockPos pos) {
        super(Bubie.STATION_MENU.get(), id);
        this.data = data;
        this.pos = pos;
        this.player = inventory.player;
        for (int i = 0; i < STORAGE; i++) addSlot(new SlotItemHandler(items, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18));
        addSlot(new FixedSlot(items, FUEL, FUEL_X, FUEL_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, INV_Y + 58));
        addDataSlots(data);
    }

    public int energy() {
        return data.get(0) * 200;
    }

    public int capacity() {
        return Math.max(1, data.get(1) * 200);
    }

    public boolean charging() {
        return data.get(3) != 0;
    }

    /** Once a second, the Bubies living at this station go to the screen's tracker (only when something changed). */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof net.minecraft.server.level.ServerPlayer server) || ticks++ % 20 != 0) return;
        com.bubie.network.StationBubiesPayload now = com.bubie.network.StationBubiesPayload.of(server.server,
                server.level().dimension(), pos, server.level().dimension());
        if (now.bubies().equals(sent)) return;
        sent = now.bubies();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(server, now);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(Bubie.STATION.get()) && player.distanceToSqr(pos.getCenter()) < 64;
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
            boolean moved = slots.get(FUEL).mayPlace(stack) && moveItemStackTo(stack, FUEL, FUEL + 1, false);
            if (!moved && !moveItemStackTo(stack, 0, STORAGE, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
