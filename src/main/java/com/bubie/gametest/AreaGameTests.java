package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import com.bubie.entity.Mode;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Working without a marked area: around the chest (or Station), with the radius from the menu. */
public class AreaGameTests {
    @BubieTest(template = BubieGameTests.FIELD, timeoutTicks = 1600)
    public static void lumberjackFindsTheTreesAroundItsChest(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.GRASS_BLOCK);
        BlockPos trunk = new BlockPos(8, 1, 7);
        for (int y = 0; y < 4; y++) helper.setBlock(trunk.above(y), Blocks.OAK_LOG);
        helper.setBlock(trunk.above(4), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
        helper.setBlock(BubieGameTests.CHEST, Blocks.CHEST);
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(4, 1, 4));
        bubie.setEnergy(40000);
        bubie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        bubie.setJob(JobType.LUMBERJACK);
        bubie.setChest(helper.absolutePos(BubieGameTests.CHEST)); // only the chest: no corners
        bubie.setWorkRadius(8);
        bubie.setMode(Mode.WORK);
        helper.assertTrue(bubie.getMarkedArea() == null && bubie.getWorkArea() != null, "it has an area without corners");
        helper.succeedWhen(() -> {
            for (int y = 0; y < 4; y++) helper.assertBlockNotPresent(Blocks.OAK_LOG, trunk.above(y));
            helper.assertTrue(BubieGameTests.inChest(helper, Items.OAK_LOG) >= 3, "the logs went into the chest; " + bubie.getStatus());
        });
    }

    @BubieTest(template = BubieGameTests.FIELD, timeoutTicks = 20)
    public static void quarryPitOpensInFrontOfTheChestNeverUnderIt(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        BlockPos chestAt = new BlockPos(8, 1, 3);
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH);
        helper.setBlock(chestAt, chest);
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(6, 1, 3));
        bubie.setJob(JobType.QUARRY);
        BlockPos c = helper.absolutePos(chestAt);
        bubie.setChest(c);
        bubie.setWorkRadius(3);
        WorkArea pit = bubie.getWorkArea();
        helper.assertTrue(pit != null, "a pit");
        helper.assertTrue(!pit.contains(c) && !pit.contains(c.below()) && !pit.contains(c.south()), "not under or next to the chest: " + pit);
        helper.assertTrue(pit.contains(c.south(2).below()) && pit.contains(c.south(7).east(3).below(10)), "in front of it: " + pit);
        helper.assertTrue(pit.min().getZ() > c.getZ(), "all of it on the front side");
        helper.assertTrue(pit.sizeX() == 7 && pit.sizeZ() == 6, "7 wide (radius 3 each side) and 6 long: " + pit);

        bubie.setJob(JobType.FARMER);
        WorkArea around = bubie.getWorkArea();
        helper.assertTrue(around.contains(c.north(3)) && around.contains(c.south(3)) && around.contains(c.east(3)),
                "the farmer works all around: " + around);
        bubie.setWorkRadius(500);
        helper.assertTrue(bubie.getWorkRadius() == bubie.maxWorkRadius() && bubie.getWorkArea().largestSide() <= bubie.maxAreaSide(),
                "the radius stops at the size limit");

        // Containers using FACING (like a Barrel) respect horizontal facing and safely fall back when vertical
        BlockPos barrelAt = new BlockPos(10, 1, 3);
        helper.setBlock(barrelAt, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.EAST));
        BlockPos b = helper.absolutePos(barrelAt);
        bubie.setChest(b);
        bubie.setJob(JobType.QUARRY);
        bubie.setWorkRadius(3);
        WorkArea barrelPit = bubie.getWorkArea();
        helper.assertTrue(barrelPit != null && barrelPit.min().getX() > b.getX(), "barrel pit opens to the east: " + barrelPit);

        helper.setBlock(barrelAt, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
        WorkArea upBarrelPit = bubie.getWorkArea();
        helper.assertTrue(upBarrelPit != null && upBarrelPit.max().getZ() < b.getZ(), "upward barrel falls back to north: " + upBarrelPit);
        helper.succeed();
    }
}
