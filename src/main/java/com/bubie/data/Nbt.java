package com.bubie.data;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.stream.Stream;

/**
 * Bubie keeps its data as a {@link CompoundTag} (the same tag goes into the Bubie item), while entities and block
 * entities now save through {@link ValueOutput}/{@link ValueInput}. These helpers move a tag in and out of those
 * flat, key by key, so a world saved by an older Bubie still loads.
 */
public final class Nbt {
    /** Every field of a map as one CompoundTag, written back out field by field. */
    private static final MapCodec<CompoundTag> FLAT = new MapCodec<>() {
        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }

        @Override
        public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
            CompoundTag tag = new CompoundTag();
            input.entries().forEach(e -> ops.getStringValue(e.getFirst()).result()
                    .ifPresent(key -> tag.put(key, ops.convertTo(NbtOps.INSTANCE, e.getSecond()))));
            return DataResult.success(tag);
        }

        @Override
        public <T> RecordBuilder<T> encode(CompoundTag tag, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            for (String key : tag.keySet()) prefix.add(key, NbtOps.INSTANCE.convertTo(ops, tag.get(key)));
            return prefix;
        }
    };

    private Nbt() {
    }

    public static void write(ValueOutput out, CompoundTag tag) {
        out.store(FLAT, tag);
    }

    public static CompoundTag read(ValueInput in) {
        return in.read(FLAT).orElseGet(CompoundTag::new);
    }

    /** The stack's fields added to {@code into} (so a "Slot" can sit beside them), as ItemStack#save did. */
    public static CompoundTag saveStack(HolderLookup.Provider registries, ItemStack stack, CompoundTag into) {
        Tag saved = ItemStack.CODEC.encode(stack, RegistryOps.create(NbtOps.INSTANCE, registries), into).getOrThrow();
        return (CompoundTag) saved;
    }

    public static CompoundTag saveStack(HolderLookup.Provider registries, ItemStack stack) {
        return saveStack(registries, stack, new CompoundTag());
    }

    /** ItemStack#parseOptional: empty for a missing or broken tag. */
    public static ItemStack parseStack(HolderLookup.Provider registries, CompoundTag tag) {
        if (tag.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), tag).result().orElse(ItemStack.EMPTY);
    }
}
