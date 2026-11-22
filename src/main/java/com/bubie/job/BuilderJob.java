package com.bubie.job;

import com.bubie.compat.BlueprintCompat;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder: builds the base on the Base Blueprint the owner handed over, one block at a time, lowest layer first,
 * taking the blocks from its chest (another Bubie can keep that chest filled). Spots with something solid in them
 * are left alone, as Base Blueprint does.
 */
public class BuilderJob extends Job {
    private Map<BlockPos, Block> steps = Map.of();
    private boolean done;

    public BuilderJob(BubieEntity bubie, WorkArea area) {
        super(bubie, area);
    }

    private void refresh() {
        List<BlueprintCompat.Step> plan = BlueprintCompat.plan(bubie);
        Map<BlockPos, Block> map = new LinkedHashMap<>();
        for (BlueprintCompat.Step s : plan) map.put(s.pos(), s.block());
        steps = map;
    }

    /** Blocks still to place, as of the last look at the plan. */
    public int remaining() {
        return steps.size();
    }

    @Override
    public boolean placesBlocks() {
        return true;
    }

    @Override
    public boolean collectsItems() {
        return false;
    }

    @Override
    public boolean plugsFluids() {
        return false;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean isTarget(ServerLevel level, BlockPos pos, BlockState state) {
        return steps.containsKey(pos) && state.canBeReplaced();
    }

    /** Lowest layer first, nearest spot in it, skipping spots that failed a moment ago. */
    @Nullable
    @Override
    public BlockPos next(JobRunner runner) {
        refresh();
        done = steps.isEmpty();
        if (done) return JobRunner.NOTHING;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 at = bubie.position();
        for (BlockPos pos : steps.keySet()) {
            if (runner.isBlacklisted(pos)) continue;
            double score = pos.getY() * 100_000.0 + at.distanceToSqr(Vec3.atCenterOf(pos));
            if (score < bestScore) {
                bestScore = score;
                best = pos;
            }
        }
        return best == null ? JobRunner.NOTHING : best;
    }

    @Nullable
    @Override
    public Item requiredItem(BlockPos pos) {
        Block block = steps.get(pos);
        return block == null ? null : block.asItem();
    }

    /** Everything still to place, by item: what to fetch from the chest. */
    @Override
    public Map<Item, Integer> shoppingList() {
        Map<Item, Integer> list = new HashMap<>();
        for (Block block : steps.values()) list.merge(block.asItem(), 1, Integer::sum);
        return list;
    }

    /** Building material stays with the Bubie instead of going back to the chest. */
    @Override
    public int keep(ItemStack stack) {
        return shoppingList().getOrDefault(stack.getItem(), 0);
    }

    /** Places the block like Base Blueprint does, asking claim and protection mods first. */
    @Override
    public boolean perform(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
        Block block = steps.get(pos);
        if (block == null) return false;
        ItemStack one = bubie.takeOne(s -> s.is(block.asItem()));
        if (one.isEmpty()) return false;
        FakePlayer player = BlockBreaker.player(bubie, ItemStack.EMPTY);
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL)
                || ForgeEventFactory.onBlockPlace(player, snapshot, Direction.UP)) {
            snapshot.restore(true, false);
            bubie.insert(one);
            return false;
        }
        SoundType sound = block.defaultBlockState().getSoundType(level, pos, bubie);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2F, sound.getPitch() * 0.8F);
        steps.remove(pos);
        return true;
    }
}
