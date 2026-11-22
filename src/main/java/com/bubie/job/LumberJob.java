package com.bubie.job;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lumberjack: finds the bottom log of a tree, fells every log of it at once (one tree takes longer than one log),
 * and plants a sapling of the same wood where the trunk stood. Log cabins are safe: a "tree" without natural leaves,
 * or touching planks, glass or a block entity, is left alone.
 */
public class LumberJob extends Job {
    private static final int MAX_LOGS = 256;

    public LumberJob(BubieEntity bubie, WorkArea area) {
        super(bubie, area);
    }

    @Override
    public boolean plugsFluids() {
        return false;
    }

    /** A tree is one target: two lumberjacks never work on the same trunk. */
    @Override
    public int claimRadius() {
        return 2;
    }

    @Override
    public boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(BlockTags.LOGS)) return false;
        BlockState below = level.getBlockState(pos.below());
        if (below.is(BlockTags.LOGS)) return false;
        if (!below.is(BlockTags.DIRT) && !below.is(Tags.Blocks.STONE) && !below.is(BlockTags.SAND)) return false;
        return tree(level, pos) != null;
    }

    @Override
    public float effort(ServerLevel level, BlockPos pos) {
        List<BlockPos> logs = tree(level, pos);
        return logs == null ? 1 : Math.min(8.0F, 1 + 0.2F * (logs.size() - 1));
    }

    /** All logs of the natural tree standing on base, top first; null if it isn't a natural tree. */
    static List<BlockPos> tree(ServerLevel level, BlockPos base) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> logs = new ArrayList<>();
        queue.add(base);
        seen.add(base);
        boolean leaves = false;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            logs.add(pos);
            if (logs.size() > MAX_LOGS) return null;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = pos.offset(dx, dy, dz);
                        if (n.getY() < base.getY() || Math.abs(n.getX() - base.getX()) > 12 || Math.abs(n.getZ() - base.getZ()) > 12) continue;
                        if (!seen.add(n)) continue;
                        BlockState s = level.getBlockState(n);
                        if (s.is(BlockTags.LOGS)) queue.add(n);
                        else if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) leaves = true;
                    }
                }
            }
            for (Direction dir : Direction.values()) {
                BlockPos n = pos.relative(dir);
                BlockState s = level.getBlockState(n);
                if (s.is(BlockTags.PLANKS) || s.is(Tags.Blocks.GLASS) || s.is(BlockTags.DOORS)
                        || level.getBlockEntity(n) != null) {
                    return null; // built by someone
                }
            }
        }
        if (!leaves) return null;
        logs.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
        return logs;
    }

    @Override
    public boolean perform(ServerLevel level, BlockPos base, BlockState state, ItemStack tool) {
        List<BlockPos> logs = tree(level, base);
        if (logs == null) return false;
        Item sapling = saplingFor(state.getBlock());
        List<BlockPos> ground = new ArrayList<>();
        for (BlockPos log : logs) {
            if (log.getY() == base.getY() && level.getBlockState(log.below()).is(BlockTags.DIRT)) ground.add(log);
        }
        boolean any = false;
        for (BlockPos log : logs) {
            ItemStack use = tool;
            if (!use.isEmpty() && use.isDamageableItem() && use.getMaxDamage() - use.getDamageValue() <= 2) use = ItemStack.EMPTY;
            if (!level.getBlockState(log).is(BlockTags.LOGS)) continue;
            if (!BlockBreaker.breakBlock(bubie, log, use)) {
                if (!any) return false;
                break;
            }
            any = true;
            tool = bubie.getTool();
        }
        // replant: big trees (2x2) get a sapling on every trunk spot
        for (BlockPos spot : ground) {
            if (!level.getBlockState(spot).isAir()) continue;
            ItemStack one = bubie.takeOne(s -> sapling != null && s.is(sapling));
            if (one.isEmpty()) one = bubie.takeOne(s -> s.is(ItemTags.SAPLINGS));
            if (one.isEmpty()) break;
            BlockBreaker.place(bubie, spot, one);
        }
        return true;
    }

    /** oak_log → oak_sapling, crimson_stem → crimson_fungus; works for most modded woods too. */
    static Item saplingFor(Block log) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(log);
        String path = id.getPath().replace("stripped_", "");
        String[][] swaps = {{"_log", "_sapling"}, {"_wood", "_sapling"}, {"_stem", "_fungus"}, {"_hyphae", "_fungus"}};
        for (String[] swap : swaps) {
            if (!path.endsWith(swap[0])) continue;
            String name = path.substring(0, path.length() - swap[0].length()) + swap[1];
            ResourceLocation saplingId = new ResourceLocation(id.getNamespace(), name);
            Item item = BuiltInRegistries.ITEM.get(saplingId);
            if (item != Items.AIR) return item;
            if (name.startsWith("mangrove")) return Items.MANGROVE_PROPAGULE;
        }
        return null;
    }

    @Override
    public int keep(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) ? 16 : 0;
    }
}
