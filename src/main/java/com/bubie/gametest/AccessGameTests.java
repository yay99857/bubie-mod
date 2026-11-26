package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/** Getting there when no path does: digging down a pit, cutting steps up a cliff, and closing the way afterwards. */
@GameTestHolder(Bubie.MOD_ID)
@PrefixGameTestTemplate(false)
public class AccessGameTests {
    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            helper.setBlock(new BlockPos(x, y, z), block);
        }
    }

    /** Holes left in a box (other than the ones listed as meant to be open). */
    private static int holes(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Set<BlockPos> open) {
        int n = 0;
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            BlockPos p = new BlockPos(x, y, z);
            if (!open.contains(p) && helper.getBlockState(p).isAir()) n++;
        }
        return n;
    }

    /** A 1x1 area seven blocks deep: no room for stairs, so it digs down under itself, climbs out and fills its way back in. */
    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 3600)
    public static void quarryDigsANarrowShaftAndClosesItsWayOut(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        fill(helper, 4, 1, 4, 12, 7, 12, Blocks.STONE);
        BubieEntity bubie = BubieGameTests.bubie(helper, new BlockPos(8, 8, 7), JobType.QUARRY, new ItemStack(Items.IRON_PICKAXE),
                new BlockPos(8, 1, 8), new BlockPos(8, 7, 8));
        Set<BlockPos> shaft = new HashSet<>();
        for (int y = 1; y <= 7; y++) shaft.add(new BlockPos(8, y, 8));
        helper.succeedWhen(() -> {
            for (BlockPos p : shaft) {
                helper.assertTrue(helper.getBlockState(p).isAir(), "shaft dug at " + p + "; " + bubie.getStatus() + " at "
                        + helper.relativePos(bubie.blockPosition()));
            }
            // it keeps the cobblestone (filler), but comes back up to its chest
            helper.assertTrue(bubie.distanceToSqr(Vec3.atCenterOf(helper.absolutePos(BubieGameTests.CHEST))) <= 6 * 6,
                    "back at its chest; " + bubie.getStatus() + " at " + helper.relativePos(bubie.blockPosition()));
            int left = holes(helper, 4, 1, 4, 12, 7, 12, shaft);
            helper.assertTrue(left == 0, left + " holes left outside the area; " + bubie.getStatus() + " at "
                    + helper.relativePos(bubie.blockPosition()));
        });
    }

    /** The tree stands on a cliff five blocks high: it cuts steps up, fells it, and fills the steps in again. */
    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 3600)
    public static void lumberjackCutsStepsUpACliff(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.GRASS_BLOCK);
        fill(helper, 0, 1, 10, 15, 5, 15, Blocks.STONE);
        BlockPos trunk = new BlockPos(8, 6, 13);
        helper.setBlock(trunk.below(), Blocks.GRASS_BLOCK);
        for (int y = 0; y < 3; y++) helper.setBlock(trunk.above(y), Blocks.OAK_LOG);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        helper.setBlock(trunk.above(3), leaves);
        BubieEntity bubie = BubieGameTests.bubie(helper, new BlockPos(8, 1, 5), JobType.LUMBERJACK, new ItemStack(Items.IRON_PICKAXE),
                new BlockPos(1, 1, 1), new BlockPos(14, 9, 14));
        helper.succeedWhen(() -> {
            for (int y = 0; y < 3; y++) {
                helper.assertTrue(!helper.getBlockState(trunk.above(y)).is(Blocks.OAK_LOG), "log " + y + " felled; "
                        + bubie.getStatus() + " at " + helper.relativePos(bubie.blockPosition()));
            }
            helper.assertTrue(BubieGameTests.inChest(helper, Items.OAK_LOG) >= 3, "logs in the chest; " + bubie.getStatus());
            int left = holes(helper, 0, 1, 10, 15, 5, 15, Set.of());
            helper.assertTrue(left == 0, left + " steps still cut in the cliff; " + bubie.getStatus() + " at "
                    + helper.relativePos(bubie.blockPosition()));
        });
    }

    /** Scars opened to reach a job are saved with the entity and still closed after a save/load cycle. */
    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 3600)
    public static void scarsSurviveEntitySaveAndLoad(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.GRASS_BLOCK);
        fill(helper, 0, 1, 10, 15, 5, 15, Blocks.STONE);
        BlockPos trunk = new BlockPos(8, 6, 13);
        helper.setBlock(trunk.below(), Blocks.GRASS_BLOCK);
        for (int y = 0; y < 3; y++) helper.setBlock(trunk.above(y), Blocks.OAK_LOG);
        helper.setBlock(trunk.above(3), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
        BubieEntity first = BubieGameTests.bubie(helper, new BlockPos(8, 1, 5), JobType.LUMBERJACK, new ItemStack(Items.IRON_PICKAXE),
                new BlockPos(1, 1, 1), new BlockPos(14, 9, 14));

        final boolean[] reloaded = {false};
        final BubieEntity[] active = {first};
        helper.succeedWhen(() -> {
            if (!reloaded[0]) {
                for (int y = 0; y < 3; y++) {
                    helper.assertTrue(!helper.getBlockState(trunk.above(y)).is(Blocks.OAK_LOG), "waiting for tree to be felled");
                }
                int opened = holes(helper, 0, 1, 10, 15, 5, 15, Set.of());
                helper.assertTrue(opened > 0, "expected open scars on the cliff before reload");
                CompoundTag saved = new CompoundTag();
                first.addAdditionalSaveData(saved);
                Vec3 pos = first.position();
                first.discard();
                BubieEntity copy = helper.spawn(Bubie.BUBIE_ENTITY.get(), helper.relativePos(first.blockPosition()));
                copy.readAdditionalSaveData(saved);
                copy.moveTo(pos.x, pos.y, pos.z, first.getYRot(), first.getXRot());
                active[0] = copy;
                reloaded[0] = true;
            }
            helper.assertTrue(BubieGameTests.inChest(helper, Items.OAK_LOG) >= 3, "logs in the chest; " + active[0].getStatus());
            int left = holes(helper, 0, 1, 10, 15, 5, 15, Set.of());
            helper.assertTrue(left == 0, left + " scars still open after reload; " + active[0].getStatus() + " at "
                    + helper.relativePos(active[0].blockPosition()));
        });
    }
}
