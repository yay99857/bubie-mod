package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import com.bubie.entity.MineMode;
import com.bubie.entity.Mode;
import com.bubie.entity.Status;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Run with ./gradlew runGameTestServer (exits non-zero when a test fails). Template "field": 16×10×16 of air over stone. */
public class BubieGameTests {
    static final String FIELD = "field";
    static final BlockPos CHEST = new BlockPos(2, 1, 2);

    static void floor(GameTestHelper helper, Block block) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) helper.setBlock(new BlockPos(x, 0, z), block);
    }

    static BubieEntity bubie(GameTestHelper helper, BlockPos at, JobType job, ItemStack tool, BlockPos a, BlockPos b) {
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), at);
        bubie.setEnergy(40000);
        bubie.setItemSlot(EquipmentSlot.MAINHAND, tool);
        bubie.setJob(job);
        bubie.setArea(WorkArea.of(helper.absolutePos(a), helper.absolutePos(b)));
        helper.setBlock(CHEST, Blocks.CHEST);
        bubie.setChest(helper.absolutePos(CHEST));
        bubie.setMode(Mode.WORK);
        return bubie;
    }

    static int inChest(GameTestHelper helper, Item item) {
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) n += chest.getItem(i).getCount();
        }
        return n;
    }

    @BubieTest(template = FIELD, timeoutTicks = 1600)
    public static void lumberjackFellsTheTreeAndReplants(GameTestHelper helper) {
        floor(helper, Blocks.GRASS_BLOCK);
        BlockPos trunk = new BlockPos(8, 1, 8);
        for (int y = 0; y < 5; y++) helper.setBlock(trunk.above(y), Blocks.OAK_LOG);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = 3; y <= 5; y++) {
                    BlockPos p = trunk.offset(dx, y, dz);
                    if ((dx != 0 || dz != 0 || y == 5) && helper.getBlockState(p).isAir()) helper.setBlock(p, leaves);
                }
            }
        }
        BubieEntity bubie = bubie(helper, new BlockPos(5, 1, 8), JobType.LUMBERJACK, new ItemStack(Items.IRON_AXE),
                new BlockPos(4, 1, 4), new BlockPos(12, 8, 12));
        bubie.insert(new ItemStack(Items.OAK_SAPLING));
        helper.succeedWhen(() -> {
            for (int y = 1; y < 5; y++) helper.assertBlockNotPresent(Blocks.OAK_LOG, trunk.above(y));
            helper.assertBlockPresent(Blocks.OAK_SAPLING, trunk);
            helper.assertTrue(inChest(helper, Items.OAK_LOG) >= 5, "5 logs in the chest; status " + bubie.getStatus()
                    + ", carrying " + bubie.storage().getItems() + ", chest " + helper.getBlockEntity(CHEST, ChestBlockEntity.class).getItem(0) + "," + helper.getBlockEntity(CHEST, ChestBlockEntity.class).getItem(1) + ", loose items " + helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class, new AABB(helper.absolutePos(trunk)).inflate(8)).size());
        });
    }

    @BubieTest(template = FIELD, timeoutTicks = 800)
    public static void farmerHarvestsAndReplants(GameTestHelper helper) {
        floor(helper, Blocks.STONE);
        for (int x = 6; x <= 8; x++) {
            helper.setBlock(new BlockPos(x, 0, 8), Blocks.FARMLAND);
            helper.setBlock(new BlockPos(x, 1, 8), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        }
        bubie(helper, new BlockPos(4, 1, 8), JobType.FARMER, ItemStack.EMPTY, new BlockPos(5, 1, 7), new BlockPos(9, 2, 9));
        helper.succeedWhen(() -> {
            for (int x = 6; x <= 8; x++) {
                BlockState crop = helper.getBlockState(new BlockPos(x, 1, 8));
                helper.assertTrue(crop.is(Blocks.WHEAT) && crop.getValue(CropBlock.AGE) == 0, "wheat replanted at " + x);
            }
            helper.assertTrue(inChest(helper, Items.WHEAT) >= 3, "3 wheat in the chest");
        });
    }

    @BubieTest(template = FIELD, timeoutTicks = 1200)
    public static void diggerTakesSandLayerByLayer(GameTestHelper helper) {
        floor(helper, Blocks.STONE);
        for (int x = 6; x <= 8; x++) for (int z = 6; z <= 8; z++) for (int y = 1; y <= 2; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.SAND);
        }
        bubie(helper, new BlockPos(4, 1, 7), JobType.DIGGER, new ItemStack(Items.IRON_SHOVEL),
                new BlockPos(6, 1, 6), new BlockPos(8, 2, 8));
        helper.succeedWhen(() -> helper.assertTrue(inChest(helper, Items.SAND) == 18, "18 sand in the chest"));
    }

    @BubieTest(template = FIELD, timeoutTicks = 1600)
    public static void quarryPlugsWaterAndLeavesStairs(GameTestHelper helper) {
        floor(helper, Blocks.STONE);
        for (int x = 6; x <= 8; x++) for (int z = 6; z <= 8; z++) for (int y = 1; y <= 2; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        // a pool of water against the east wall of the area
        for (BlockPos p : new BlockPos[]{new BlockPos(9, 1, 7), new BlockPos(10, 2, 7), new BlockPos(9, 2, 6), new BlockPos(9, 2, 8)}) {
            helper.setBlock(p, Blocks.STONE);
        }
        helper.setBlock(new BlockPos(9, 2, 7), Blocks.WATER);
        BubieEntity bubie = bubie(helper, new BlockPos(4, 1, 7), JobType.QUARRY, new ItemStack(Items.STONE_PICKAXE),
                new BlockPos(6, 1, 6), new BlockPos(8, 2, 8));
        bubie.insert(new ItemStack(Items.COBBLESTONE, 8));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(9, 2, 7));
            // stairs: top layer keeps (6,2,6), the layer below keeps (7,1,6)
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(6, 2, 6));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(7, 1, 6));
            for (int x = 6; x <= 8; x++) for (int z = 6; z <= 8; z++) for (int y = 1; y <= 2; y++) {
                BlockPos p = new BlockPos(x, y, z);
                if (p.equals(new BlockPos(6, 2, 6)) || p.equals(new BlockPos(7, 1, 6))) continue;
                helper.assertBlockNotPresent(Blocks.STONE, p);
            }
            helper.assertTrue(bubie.getStatus() == Status.DONE, "quarry reports done, is " + bubie.getStatus());
        });
    }

    @BubieTest(template = FIELD, timeoutTicks = 800)
    public static void minerTakesExposedOre(GameTestHelper helper) {
        floor(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.IRON_ORE);
        bubie(helper, new BlockPos(4, 1, 7), JobType.MINER, new ItemStack(Items.STONE_PICKAXE),
                new BlockPos(5, 0, 5), new BlockPos(9, 2, 9));
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(Blocks.IRON_ORE, new BlockPos(7, 1, 7));
            helper.assertTrue(inChest(helper, Items.RAW_IRON) >= 1, "raw iron in the chest");
        });
    }

    @BubieTest(template = FIELD, timeoutTicks = 400)
    public static void minerWaitsForABetterPickaxe(GameTestHelper helper) {
        floor(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.IRON_ORE);
        BubieEntity bubie = bubie(helper, new BlockPos(4, 1, 7), JobType.MINER, new ItemStack(Items.WOODEN_PICKAXE),
                new BlockPos(5, 0, 5), new BlockPos(9, 2, 9));
        helper.succeedWhen(() -> {
            helper.assertTrue(bubie.getStatus() == Status.NO_TOOL, "status should be NO_TOOL, is " + bubie.getStatus());
            helper.assertBlockPresent(Blocks.IRON_ORE, new BlockPos(7, 1, 7));
        });
    }

    @BubieTest(template = FIELD, timeoutTicks = 1600)
    public static void tunnelMinerDigsBranchesAndTakesOre(GameTestHelper helper) {
        floor(helper, Blocks.STONE);
        for (int x = 3; x <= 13; x++) for (int z = 4; z <= 10; z++) for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(3, 1, 7), Blocks.AIR); // the way in
        helper.setBlock(new BlockPos(3, 2, 7), Blocks.AIR);
        helper.setBlock(new BlockPos(9, 3, 7), Blocks.COAL_ORE); // right above the tunnel
        BubieEntity bubie = bubie(helper, new BlockPos(2, 1, 7), JobType.MINER, new ItemStack(Items.IRON_PICKAXE),
                new BlockPos(4, 1, 5), new BlockPos(12, 2, 9));
        bubie.setMineMode(MineMode.TUNNEL);
        helper.succeedWhen(() -> {
            for (int x = 4; x <= 12; x++) {
                helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(x, 1, 7));
                helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(x, 2, 7));
            }
            // a branch at the 5th block (x = 8) both ways
            helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(8, 1, 5));
            helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(8, 1, 9));
            helper.assertBlockNotPresent(Blocks.COAL_ORE, new BlockPos(9, 3, 7));
            helper.assertTrue(bubie.getStatus() == Status.DONE, "tunnel done, is " + bubie.getStatus());
        });
    }

    @BubieTest(template = FIELD, timeoutTicks = 100)
    public static void shutsDownIntoItsItemKeepingItsThings(GameTestHelper helper) {
        floor(helper, Blocks.STONE);
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(7, 1, 7));
        bubie.insert(new ItemStack(Items.DIAMOND, 3));
        bubie.setLook(new com.bubie.entity.Look(1, 3, 2, 6, 1));
        bubie.kill(helper.getLevel());
        helper.succeedWhen(() -> {
            AABB box = new AABB(helper.absolutePos(new BlockPos(7, 1, 7))).inflate(3);
            ItemEntity drop = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                    .filter(e -> e.getItem().is(Bubie.BUBIE_ITEM.get())).findFirst().orElse(null);
            helper.assertTrue(drop != null, "the Bubie item dropped");
            var data = drop.getItem().get(DataComponents.CUSTOM_DATA);
            helper.assertTrue(data != null && data.copyTag().getCompoundOrEmpty("Bubie").getListOrEmpty("Storage").size() == 1,
                    "the item keeps the diamonds");
            var saved = com.bubie.entity.Look.load(data.copyTag().getCompoundOrEmpty("Bubie").getCompoundOrEmpty("Look"));
            helper.assertTrue(saved.equals(new com.bubie.entity.Look(1, 3, 2, 6, 1)), "the item keeps the look, got " + saved);
            helper.assertTrue(com.bubie.entity.Look.unpack(saved.pack()).equals(saved), "look packs and unpacks");
        });
    }

    /** Coal is not fuel any more: the fuel slot refuses it and only redstone (or FE) charges the Bubie. */
    @BubieTest(template = FIELD)
    public static void runsOnRedstoneNotCoal(GameTestHelper helper) {
        helper.assertTrue(!BubieEntity.isFuel(new ItemStack(Items.COAL)), "coal is not fuel");
        helper.assertTrue(!BubieEntity.isFuel(new ItemStack(Items.OAK_LOG)), "wood is not fuel");
        helper.assertTrue(BubieEntity.isFuel(new ItemStack(Items.REDSTONE)), "redstone is");
        helper.assertTrue(BubieEntity.fuelValue(new ItemStack(Items.REDSTONE_BLOCK)) == 9 * BubieEntity.fuelValue(new ItemStack(Items.REDSTONE)),
                "a block is nine dusts");
        helper.assertTrue(!com.bubie.block.StationBlockEntity.isStationFuel(new ItemStack(Items.COAL)), "nor for the station");
        helper.succeed();
    }

    @BubieTest(template = FIELD, timeoutTicks = 200)
    public static void burnsFuelWhenTheBatteryIsEmpty(GameTestHelper helper) {
        floor(helper, Blocks.GRASS_BLOCK);
        BubieEntity bubie = bubie(helper, new BlockPos(7, 1, 7), JobType.FARMER, ItemStack.EMPTY,
                new BlockPos(5, 1, 5), new BlockPos(9, 2, 9));
        bubie.setEnergy(0);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(bubie.getStatus() == Status.NO_ENERGY, "no energy, is " + bubie.getStatus());
            bubie.extra().setItem(BubieEntity.FUEL_SLOT, new ItemStack(Items.REDSTONE, 2));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(bubie.getEnergy() >= 6000, "one redstone burnt, energy " + bubie.getEnergy());
            helper.assertTrue(bubie.extra().getItem(BubieEntity.FUEL_SLOT).getCount() == 1, "one redstone left");
        });
    }

    /** A name from the menu: formatting stripped, cut to 32, empty back to "Bubie"; it stays on the item. */
    @BubieTest(template = FIELD, timeoutTicks = 20)
    public static void renameFromTheMenu(GameTestHelper helper) {
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(7, 1, 7));
        bubie.rename("  §cDigger  ");
        helper.assertTrue(bubie.hasCustomName() && bubie.getCustomName().getString().equals("Digger"),
                "formatting and spaces stripped, got " + bubie.getCustomName());
        helper.assertTrue(!bubie.isCustomNameVisible(), "the name shows when looked at, like a name tag");
        bubie.rename("A".repeat(60));
        helper.assertTrue(bubie.getCustomName().getString().length() == BubieEntity.MAX_NAME, "cut to " + BubieEntity.MAX_NAME);
        bubie.rename("Miner of the north");
        ItemStack item = com.bubie.item.BubieItem.capture(bubie);
        helper.assertTrue(item.getHoverName().getString().equals("Miner of the north"), "the item keeps the name, got " + item.getHoverName().getString());
        bubie.rename("   ");
        helper.assertTrue(!bubie.hasCustomName(), "an empty name clears it");
        helper.succeed();
    }
}
