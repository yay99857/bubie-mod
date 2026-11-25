package com.bubie.block;

import com.bubie.Bubie;
import com.bubie.BubieConfig;
import com.bubie.entity.BubieEntity;
import com.bubie.menu.StationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import com.bubie.data.Inv;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import javax.annotation.Nullable;

public class StationBlockEntity extends BlockEntity implements MenuProvider {
    public static final int STORAGE = 27;
    public static final int FUEL = 27;
    public static final int FE_CAPACITY = 200_000;
    /** Bubie energy given per 10 ticks to each Bubie at home (full battery in about a minute). */
    private static final int CHARGE_PER_CYCLE = 400;

    private final ItemStacksResourceHandler items = new ItemStacksResourceHandler(STORAGE + 1) {
        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return slot != FUEL || isStationFuel(resource.toStack());
        }

        @Override
        protected void onContentsChanged(int slot, ItemStack previous) {
            setChanged();
        }
    };
    /**
     * What pipes, hoppers, funnels and Bubies see: the fuel slot first (insert only, so coal sent by a pipe tops it
     * up before anything else), then the 27 storage slots.
     */
    private final ResourceHandler<ItemResource> storage = new ResourceHandler<>() {
        private int real(int slot) {
            return slot == 0 ? FUEL : slot - 1;
        }

        @Override
        public int size() {
            return STORAGE + 1;
        }

        @Override
        public ItemResource getResource(int slot) {
            return items.getResource(real(slot));
        }

        @Override
        public long getAmountAsLong(int slot) {
            return items.getAmountAsLong(real(slot));
        }

        @Override
        public long getCapacityAsLong(int slot, ItemResource resource) {
            return items.getCapacityAsLong(real(slot), resource);
        }

        @Override
        public boolean isValid(int slot, ItemResource resource) {
            return items.isValid(real(slot), resource);
        }

        @Override
        public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
            return items.insert(real(slot), resource, amount, tx);
        }

        @Override
        public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
            return slot == 0 ? 0 : items.extract(real(slot), resource, amount, tx);
        }
    };
    /** Accepts FE from cables; only the station itself spends it. */
    private final class StationEnergy extends SimpleEnergyHandler {
        StationEnergy() {
            super(FE_CAPACITY, 4000, 0);
        }

        @Override
        protected void onEnergyChanged(int previous) {
            setChanged();
        }

        int stored() {
            return energy;
        }

        void use(int amount) {
            set(Math.max(0, energy - amount));
        }
    }

    private final StationEnergy energy = new StationEnergy();
    /** Bubie energy already paid for with burnt fuel. */
    private int fuelBuffer;
    private boolean chunksLoaded;
    private int charging;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.stored() / 200;
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

    public ItemStacksResourceHandler items() {
        return items;
    }

    public ItemStack stackIn(int slot) {
        return Inv.get(items, slot);
    }

    public void setStack(int slot, ItemStack stack) {
        items.set(slot, ItemResource.of(stack), stack.getCount());
    }

    public ResourceHandler<ItemResource> storageHandler() {
        return storage;
    }

    public EnergyHandler energy() {
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
            if (level.getRandom().nextInt(3) == 0) {
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
            int units = fePer <= 0 ? need - given : Math.min(need - given, energy.stored() / fePer);
            if (units > 0) {
                energy.use(units * fePer);
                given += units;
            }
        }
        if (given < need) {
            ItemStack fuel = stackIn(FUEL);
            int burn = com.bubie.entity.BubieEntity.fuelValue(fuel);
            if (burn > 0) {
                ItemStack remainder = com.bubie.entity.BubieEntity.remainder(fuel);
                fuel.shrink(1);
                setStack(FUEL, fuel.isEmpty() && !remainder.isEmpty() ? remainder : fuel);
                fuelBuffer += burn;
                int more = Math.min(need - given, fuelBuffer);
                fuelBuffer -= more;
                given += more;
            }
        }
        if (given > 0) setChanged();
        return given;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        // the layout item handlers used ("Items": {Items: [{Slot, ...}], Size}), so older stations load
        ValueOutput saved = out.child("Items");
        ValueOutput.TypedOutputList<ItemStackWithSlot> list = saved.list("Items", ItemStackWithSlot.CODEC);
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = stackIn(i);
            if (!stack.isEmpty()) list.add(new ItemStackWithSlot(i, stack));
        }
        saved.putInt("Size", items.size());
        out.putInt("Energy", energy.stored());
        out.putInt("FuelBuffer", fuelBuffer);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        for (int i = 0; i < items.size(); i++) items.set(i, ItemResource.EMPTY, 0);
        in.childOrEmpty("Items").listOrEmpty("Items", ItemStackWithSlot.CODEC).forEach(entry -> {
            if (entry.slot() >= 0 && entry.slot() < items.size()) setStack(entry.slot(), entry.stack());
        });
        energy.set(Math.min(FE_CAPACITY, Math.max(0, in.getIntOr("Energy", 0))));
        fuelBuffer = in.getIntOr("FuelBuffer", 0);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level == null) return;
        for (int i = 0; i < items.size(); i++) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stackIn(i));
        }
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
