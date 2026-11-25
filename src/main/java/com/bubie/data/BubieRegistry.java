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
    /** Kept as one tag in the layout the old SavedData used. */
    private static final com.mojang.serialization.Codec<BubieRegistry> CODEC =
            CompoundTag.CODEC.xmap(BubieRegistry::load, r -> r.save(new CompoundTag()));
    private static final net.minecraft.world.level.saveddata.SavedDataType<BubieRegistry> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(com.bubie.Bubie.MOD_ID, "registry"), BubieRegistry::new, CODEC);
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
            return new Sighting(tag.getStringOr("Name", ""), tag.getStringOr("Dim", ""), tag.getLongOr("Pos", 0L), tag.getLongOr("Home", 0L),
                    tag.getIntOr("Doing", 0), tag.getIntOr("Status", 0), tag.getIntOr("Face", 0), tag.getIntOr("Energy", 0), tag.getIntOr("Look", 0));
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
        return server.getDataStorage().computeIfAbsent(TYPE);
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

    private CompoundTag save(CompoundTag tag) {
        ListTag owners = new ListTag();
        byOwner.forEach((owner, bubies) -> {
            CompoundTag entry = new CompoundTag();
            entry.store("Owner", net.minecraft.core.UUIDUtil.CODEC, owner);
            ListTag list = new ListTag();
            for (UUID id : bubies) list.add(new net.minecraft.nbt.IntArrayTag(net.minecraft.core.UUIDUtil.uuidToIntArray(id)));
            entry.put("Bubies", list);
            owners.add(entry);
        });
        tag.put("Owners", owners);
        ListTag seen = new ListTag();
        sightings.forEach((bubie, sighting) -> {
            CompoundTag entry = sighting.save();
            entry.store("Bubie", net.minecraft.core.UUIDUtil.CODEC, bubie);
            seen.add(entry);
        });
        tag.put("Sightings", seen);
        return tag;
    }

    private static BubieRegistry load(CompoundTag tag) {
        BubieRegistry registry = new BubieRegistry();
        for (Tag t : tag.getListOrEmpty("Owners")) {
            CompoundTag entry = (CompoundTag) t;
            Set<UUID> set = new HashSet<>();
            for (Tag id : entry.getListOrEmpty("Bubies")) {
                if (id instanceof net.minecraft.nbt.IntArrayTag ints && ints.size() == 4) set.add(net.minecraft.core.UUIDUtil.uuidFromIntArray(ints.getAsIntArray()));
            }
            registry.byOwner.put(entry.read("Owner", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), set);
        }
        for (Tag t : tag.getListOrEmpty("Sightings")) {
            CompoundTag entry = (CompoundTag) t;
            entry.read("Bubie", net.minecraft.core.UUIDUtil.CODEC).ifPresent(id -> registry.sightings.put(id, Sighting.load(entry)));
        }
        return registry;
    }
}
