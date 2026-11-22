package com.bubie.block;

import com.bubie.Bubie;
import com.bubie.BubieConfig;
import com.bubie.entity.BubieEntity;
import com.bubie.menu.StationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;

public class StationBlockEntity extends BlockEntity implements MenuProvider {
    public static final int STORAGE = 27;
    public static final int FUEL = 27;
    public static final int FE_CAPACITY = 200_000;
    /** Bubie energy given per 10 ticks to each Bubie at home (full battery in about a minute). */
    private static final int CHARGE_PER_CYCLE = 400;

    private final ItemStackHandler items = new ItemStackHandler(STORAGE + 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot != FUEL || isStationFuel(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    /**
     * What pipes, hoppers, funnels and Bubies see: the fuel slot first (insert only, so coal sent by a pipe tops it
     * up before anything else), then the 27 storage slots.
     */
    private final IItemHandler storage = new IItemHandler() {
        private int real(int slot) {
            return slot == 0 ? FUEL : slot - 1;
        }

        @Override
        public int getSlots() {
            return STORAGE + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(real(slot));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return items.insertItem(real(slot), stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 ? ItemStack.EMPTY : items.extractItem(real(slot), amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(real(slot));
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return items.isItemValid(real(slot), stack);
        }
    };
    /** Accepts FE from cables; only the station itself spends it. */
    private final class StationEnergy extends EnergyStorage {
        StationEnergy() {
            super(FE_CAPACITY, 4000, 0);
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            int got = super.receiveEnergy(amount, simulate);
            if (got > 0 && !simulate) setChanged();
            return got;
        }

        void use(int amount) {
            energy = Math.max(0, energy - amount);
        }
    }

    private final StationEnergy energy = new StationEnergy();
    private LazyOptional<IItemHandler> storageCap = LazyOptional.of(() -> storage);
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);
    /** Bubie energy already paid for with burnt fuel. */
    private int fuelBuffer;
    private boolean chunksLoaded;
    private int charging;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored() / 200;
                case 1 -> FE_CAPACITY / 200;
                case 2 -> fuelBuffer / 100;
                case 3 -> charging;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    /** Redstone dust or blocks, like the Bubie itself (coal is not fuel). */
    public static boolean isStationFuel(ItemStack stack) {
        return com.bubie.entity.BubieEntity.fuelValue(stack) > 0;
    }

    public StationBlockEntity(BlockPos pos, BlockState state) {
        super(Bubie.STATION_BE.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public IItemHandler storageHandler() {
        return storage;
    }

    public EnergyStorage energy() {
        return energy;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StationBlockEntity be) {
        ServerLevel server = (ServerLevel) level;
        boolean wantChunks = BubieConfig.STATION_LOADS_CHUNKS.get();
        if (wantChunks != be.chunksLoaded) {
            StationChunks.set(server, pos, wantChunks);
            be.chunksLoaded = wantChunks;
        }
        if (level.getGameTime() % 10 != 0) return;
        be.charging = 0;
        for (BubieEntity bubie : level.getEntitiesOfClass(BubieEntity.class, new AABB(pos).inflate(3.0))) {
            if (!pos.equals(bubie.getHome())) continue;
            int need = Math.min(CHARGE_PER_CYCLE, bubie.getMaxEnergy() - bubie.getEnergy());
            if (need <= 0) continue;
            int given = be.supply(need);
            if (given <= 0) continue;
            bubie.setEnergy(bubie.getEnergy() + given);
            be.charging = 1;
            if (level.random.nextInt(3) == 0) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, bubie.getX(), bubie.getY() + 0.5, bubie.getZ(),
                        3, 0.2, 0.3, 0.2, 0.05);
            }
        }
    }

    /** Pays for Bubie energy: fuel already burnt, then FE, then burns another fuel item. */
    private int supply(int need) {
        int given = 0;
        int fromBuffer = Math.min(need, fuelBuffer);
        fuelBuffer -= fromBuffer;
        given += fromBuffer;
        int fePer = BubieConfig.FE_PER_ENERGY.get();
        if (given < need) {
            int units = fePer <= 0 ? need - given : Math.min(need - given, energy.getEnergyStored() / fePer);
            if (units > 0) {
                energy.use(units * fePer);
                given += units;
            }
        }
        if (given < need) {
            ItemStack fuel = items.getStackInSlot(FUEL);
            int burn = com.bubie.entity.BubieEntity.fuelValue(fuel);
            if (burn > 0) {
                ItemStack remainder = fuel.getCraftingRemainingItem();
                items.extractItem(FUEL, 1, false);
                if (items.getStackInSlot(FUEL).isEmpty() && !remainder.isEmpty()) items.setStackInSlot(FUEL, remainder);
                fuelBuffer += burn;
                int more = Math.min(need - given, fuelBuffer);
                fuelBuffer -= more;
                given += more;
            }
        }
        if (given > 0) setChanged();
        return given;
    }

    /** Pipes, hoppers and cables, on every side: the storage (fuel slot first) and the FE buffer. */
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return storageCap.cast();
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        storageCap.invalidate();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        storageCap = LazyOptional.of(() -> storage);
        energyCap = LazyOptional.of(() -> energy);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.put("Energy", energy.serializeNBT());
        tag.putInt("FuelBuffer", fuelBuffer);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.deserializeNBT(tag.getCompound("Items"));
        if (tag.contains("Energy")) energy.deserializeNBT(tag.get("Energy"));
        fuelBuffer = tag.getInt("FuelBuffer");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.bubie.bubie_station");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new StationMenu(id, inventory, this, data);
    }
}
