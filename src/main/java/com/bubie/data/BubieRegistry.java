package com.bubie.data;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Which Bubies each player has placed, for the per-player limit; counts unloaded ones too. */
public class BubieRegistry extends SavedData {
    private static final String NAME = "bubie_registry";
    private final Map<UUID, Set<UUID>> byOwner = new HashMap<>();

    public static BubieRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BubieRegistry::load, BubieRegistry::new, NAME);
    }

    public int count(UUID owner) {
        return byOwner.getOrDefault(owner, Set.of()).size();
    }

    public void add(UUID owner, UUID bubie) {
        if (byOwner.computeIfAbsent(owner, k -> new HashSet<>()).add(bubie)) setDirty();
    }

    public void remove(@Nullable UUID owner, UUID bubie) {
        if (owner == null) return;
        Set<UUID> set = byOwner.get(owner);
        if (set != null && set.remove(bubie)) {
            if (set.isEmpty()) byOwner.remove(owner);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag owners = new ListTag();
        byOwner.forEach((owner, bubies) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            ListTag list = new ListTag();
            for (UUID id : bubies) list.add(NbtUtils.createUUID(id));
            entry.put("Bubies", list);
            owners.add(entry);
        });
        tag.put("Owners", owners);
        return tag;
    }

    private static BubieRegistry load(CompoundTag tag) {
        BubieRegistry registry = new BubieRegistry();
        for (Tag t : tag.getList("Owners", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            Set<UUID> set = new HashSet<>();
            for (Tag id : entry.getList("Bubies", Tag.TAG_INT_ARRAY)) set.add(NbtUtils.loadUUID(id));
            registry.byOwner.put(entry.getUUID("Owner"), set);
        }
        return registry;
    }
}
