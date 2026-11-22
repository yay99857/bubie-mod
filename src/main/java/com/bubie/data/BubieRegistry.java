package com.bubie.data;

import net.minecraft.core.HolderLookup;
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
    private final Map<UUID, Sighting> sightings = new HashMap<>();

    /**
     * Where a Bubie that has a home was last seen and what it was doing, for its Station's tracker. Kept for
     * unloaded Bubies too: that is the point.
     *
     * @param doing the job's ordinal while working, else -1 - the mode's ordinal
     */
    public record Sighting(String name, String dimension, long pos, long home, int doing, int status, int face, int energy,
                           int look) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putString("Dim", dimension);
            tag.putLong("Pos", pos);
            tag.putLong("Home", home);
            tag.putInt("Doing", doing);
            tag.putInt("Status", status);
            tag.putInt("Face", face);
            tag.putInt("Energy", energy);
            tag.putInt("Look", look);
            return tag;
        }

        static Sighting load(CompoundTag tag) {
            return new Sighting(tag.getString("Name"), tag.getString("Dim"), tag.getLong("Pos"), tag.getLong("Home"),
                    tag.getInt("Doing"), tag.getInt("Status"), tag.getInt("Face"), tag.getInt("Energy"), tag.getInt("Look"));
        }
    }

    /** A Bubie reports in (every couple of seconds while it is loaded). */
    public void sight(UUID bubie, Sighting now) {
        if (!now.equals(sightings.put(bubie, now))) setDirty();
    }

    /** The Bubie is gone for good, or no longer has a home. */
    public void forget(UUID bubie) {
        if (sightings.remove(bubie) != null) setDirty();
    }

    /** The Bubies whose home is this Station, by name. */
    public java.util.List<Sighting> livingAt(String dimension, net.minecraft.core.BlockPos station) {
        long home = station.asLong();
        java.util.List<Sighting> list = new java.util.ArrayList<>();
        for (Sighting s : sightings.values()) if (s.home() == home && s.dimension().equals(dimension)) list.add(s);
        list.sort(java.util.Comparator.comparing(Sighting::name).thenComparingLong(Sighting::pos));
        return list;
    }

    public static BubieRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BubieRegistry::new, BubieRegistry::load), NAME);
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
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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
        ListTag seen = new ListTag();
        sightings.forEach((bubie, sighting) -> {
            CompoundTag entry = sighting.save();
            entry.putUUID("Bubie", bubie);
            seen.add(entry);
        });
        tag.put("Sightings", seen);
        return tag;
    }

    private static BubieRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        BubieRegistry registry = new BubieRegistry();
        for (Tag t : tag.getList("Owners", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            Set<UUID> set = new HashSet<>();
            for (Tag id : entry.getList("Bubies", Tag.TAG_INT_ARRAY)) set.add(NbtUtils.loadUUID(id));
            registry.byOwner.put(entry.getUUID("Owner"), set);
        }
        for (Tag t : tag.getList("Sightings", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            if (entry.hasUUID("Bubie")) registry.sightings.put(entry.getUUID("Bubie"), Sighting.load(entry));
        }
        return registry;
    }
}
