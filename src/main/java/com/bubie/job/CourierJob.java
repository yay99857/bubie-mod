package com.bubie.job;

import com.bubie.block.StationBlockEntity;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import com.bubie.data.Inv;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Courier: empties the containers inside its area (another Bubie's chest, a farm's hopper, a furnace's output) into
 * its own chest. When that chest is a Builder's, it carries only what the Builder's plan still needs.
 * Containers are read from below, like a hopper, so furnaces give their output and keep their fuel.
 */
public class CourierJob extends Job {
    /** How far to look for Builders that use the same chest. */
    private static final double BUILDER_RANGE = 96;
    private static final int RECHECK = 40;

    @Nullable
    private Map<Item, Integer> wanted;
    private long wantedAt = -RECHECK;

    public CourierJob(BubieEntity bubie, WorkArea area) {
        super(bubie, area);
    }

    @Override
    public boolean instant() {
        return true;
    }

    @Override
    public boolean plugsFluids() {
        return false;
    }

    @Override
    public boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        if (isDestination(level, pos, state)) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be instanceof StationBlockEntity) return false;
        ResourceHandler<ItemResource> source = source(level, pos);
        if (source == null) return false;
        for (int slot = 0; slot < source.size(); slot++) {
            ItemStack stack = Inv.extract(source, slot, 64, true);
            if (!stack.isEmpty() && amountWanted(level, stack.getItem()) > 0) return true;
        }
        return false;
    }

    /** Its own chest (both halves of a double chest) and its station are where things go, never where they come from. */
    private boolean isDestination(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos chest = bubie.getChest();
        if (pos.equals(chest) || pos.equals(bubie.getHome())) return true;
        if (chest != null && state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return pos.relative(ChestBlock.getConnectedDirection(state)).equals(chest);
        }
        return false;
    }

    @Nullable
    static ResourceHandler<ItemResource> source(ServerLevel level, BlockPos pos) {
        ResourceHandler<ItemResource> handler = Inv.at(level, pos, Direction.DOWN);
        return handler != null ? handler : Inv.at(level, pos, null);
    }

    /** Takes as much as it can carry (and the builder needs) from the container. */
    @Override
    public boolean perform(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        ResourceHandler<ItemResource> source = source(level, pos);
        if (source == null) return false;
        wantedAt = -RECHECK; // count again from scratch
        boolean took = false;
        for (int slot = 0; slot < source.size(); slot++) {
            ItemStack peek = Inv.extract(source, slot, 64, true);
            if (peek.isEmpty()) continue;
            int want = Math.min(peek.getCount(), amountWanted(level, peek.getItem()));
            if (want <= 0) continue;
            ItemStack got = Inv.extract(source, slot, want, false);
            if (got.isEmpty()) continue;
            ItemStack rest = bubie.insert(got);
            if (rest.getCount() < got.getCount()) took = true;
            if (!rest.isEmpty()) {
                Inv.insert(source, slot, rest, false); // its storage is full: put the rest back
                break;
            }
            if (wanted != null) wanted.merge(got.getItem(), -got.getCount(), Integer::sum);
        }
        return took;
    }

    /**
     * How many of an item to carry. Everything, unless the chest belongs to Builders: then what their plans still
     * need, less what they carry, what the chest has and what this courier already holds.
     */
    private int amountWanted(ServerLevel level, Item item) {
        long now = level.getGameTime();
        if (now - wantedAt >= RECHECK) {
            wanted = builderNeeds(level);
            wantedAt = now;
        }
        if (wanted == null) return Integer.MAX_VALUE;
        return Math.max(0, wanted.getOrDefault(item, 0));
    }

    @Nullable
    private Map<Item, Integer> builderNeeds(ServerLevel level) {
        BlockPos chest = bubie.getChest();
        if (chest == null) return null;
        Map<Item, Integer> need = null;
        AABB around = new AABB(chest).inflate(BUILDER_RANGE);
        for (BubieEntity other : level.getEntitiesOfClass(BubieEntity.class, around,
                b -> b != bubie && b.getJob() == JobType.BUILDER && chest.equals(b.getChest()))) {
            if (need == null) need = new HashMap<>();
            Job job = other.runner().job();
            if (!(job instanceof BuilderJob builder)) continue;
            for (Map.Entry<Item, Integer> e : builder.shoppingList().entrySet()) {
                need.merge(e.getKey(), e.getValue() - other.count(e.getKey()), Integer::sum);
            }
        }
        if (need == null) return null;
        ResourceHandler<ItemResource> dest = Inv.at(level, chest, null);
        if (dest != null) {
            for (int slot = 0; slot < dest.size(); slot++) {
                ItemStack s = Inv.get(dest, slot);
                if (!s.isEmpty() && need.containsKey(s.getItem())) need.merge(s.getItem(), -s.getCount(), Integer::sum);
            }
        }
        for (Map.Entry<Item, Integer> e : need.entrySet()) e.setValue(e.getValue() - bubie.count(e.getKey()));
        return need;
    }
}
