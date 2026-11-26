package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import com.bubie.entity.Mode;
import com.bubie.entity.Status;
import com.bubie.job.Claims;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Bubies working together: the courier, shared targets, and getting stuck. */
@GameTestHolder(Bubie.MOD_ID)
@PrefixGameTestTemplate(false)
public class TeamGameTests {
    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 1200)
    public static void courierEmptiesChestsAndFurnaceOutputs(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        BlockPos store = new BlockPos(12, 1, 12), furnace = new BlockPos(12, 1, 8);
        helper.setBlock(store, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(store);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 30));
        chest.setItem(7, new ItemStack(Items.OAK_LOG, 12));
        helper.setBlock(furnace, Blocks.FURNACE);
        AbstractFurnaceBlockEntity oven = (AbstractFurnaceBlockEntity) helper.getBlockEntity(furnace);
        oven.setItem(1, new ItemStack(Items.COAL, 8)); // fuel stays
        oven.setItem(2, new ItemStack(Items.IRON_INGOT, 5)); // output goes
        BubieEntity bubie = BubieGameTests.bubie(helper, new BlockPos(6, 1, 6), JobType.COURIER, ItemStack.EMPTY,
                new BlockPos(10, 1, 6), new BlockPos(14, 2, 14));
        helper.succeedWhen(() -> {
            helper.assertTrue(BubieGameTests.inChest(helper, Items.COBBLESTONE) == 30, "30 cobblestone carried; status "
                    + bubie.getStatus() + ", carrying " + bubie.storage());
            helper.assertTrue(BubieGameTests.inChest(helper, Items.OAK_LOG) == 12, "12 logs carried");
            helper.assertTrue(BubieGameTests.inChest(helper, Items.IRON_INGOT) == 5, "the furnace output carried");
            helper.assertTrue(oven.getItem(1).getCount() == 8, "the furnace kept its fuel");
        });
    }

    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 2400)
    public static void twoLumberjacksNeverShareATree(GameTestHelper helper) {
        Claims.clear();
        BubieGameTests.floor(helper, Blocks.GRASS_BLOCK);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        BlockPos[] trunks = {new BlockPos(5, 1, 6), new BlockPos(11, 1, 6), new BlockPos(8, 1, 11)};
        for (BlockPos trunk : trunks) {
            for (int y = 0; y < 4; y++) helper.setBlock(trunk.above(y), Blocks.OAK_LOG);
            helper.setBlock(trunk.above(4), leaves);
        }
        BubieEntity a = BubieGameTests.bubie(helper, new BlockPos(8, 1, 3), JobType.LUMBERJACK, new ItemStack(Items.IRON_AXE),
                new BlockPos(2, 1, 3), new BlockPos(14, 7, 14));
        BubieEntity b = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(8, 1, 4));
        b.setEnergy(40000);
        b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        b.setJob(JobType.LUMBERJACK);
        b.setArea(a.getMarkedArea());
        b.setChest(a.getChest());
        b.setMode(Mode.WORK);
        helper.onEachTick(() -> {
            BlockPos ta = a.runner().currentTarget(), tb = b.runner().currentTarget();
            if (ta != null && tb != null && Math.abs(ta.getX() - tb.getX()) <= 1 && Math.abs(ta.getZ() - tb.getZ()) <= 1) {
                helper.fail("both Bubies on the same tree: " + ta + " and " + tb);
            }
        });
        helper.succeedWhen(() -> {
            for (BlockPos trunk : trunks) {
                for (int y = 1; y < 4; y++) helper.assertBlockNotPresent(Blocks.OAK_LOG, trunk.above(y));
            }
        });
    }

    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 600)
    public static void boxedInBubieSaysItIsStuck(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        for (int x = 9; x <= 14; x++) for (int z = 9; z <= 14; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.SAND);
        BlockPos at = new BlockPos(4, 1, 4);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int y = 0; y < 3; y++) helper.setBlock(at.offset(dx, y, dz), Blocks.GLASS);
            }
        }
        helper.setBlock(at.above(2), Blocks.GLASS);
        helper.setBlock(at.below(), Blocks.GLASS); // a glass floor too: stone under it it could dig its way out through
        BubieEntity bubie = BubieGameTests.bubie(helper, at, JobType.DIGGER, new ItemStack(Items.IRON_SHOVEL),
                new BlockPos(9, 1, 9), new BlockPos(14, 1, 14));
        helper.succeedWhen(() -> helper.assertTrue(bubie.getStatus() == Status.STUCK, "status " + bubie.getStatus()));
    }
}
