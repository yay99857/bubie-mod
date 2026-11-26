package com.bubie.gametest;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import com.bubie.Bubie;
import com.bubie.compat.BlueprintCompat;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import com.bubie.entity.Mode;
import com.bubie.entity.Status;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builder scenarios; only loaded when Base Blueprint is installed (they use its classes). */
final class BuilderScenarios {
    private static final BlockPos CHEST = new BlockPos(2, 1, 2);

    private BuilderScenarios() {
    }

    private static ItemStack blueprint(int size, int walls, boolean filled, Block material) {
        ItemStack stack = new ItemStack(BaseBlueprint.BLUEPRINT.get());
        ModComponents.setSettings(stack, new BlueprintSettings(BlueprintSettings.Shape.SQUARE, size, size,
                filled ? BlueprintSettings.Floor.FILLED : BlueprintSettings.Floor.OUTLINE, walls, true,
                BlueprintSettings.Roof.NONE, false, BlueprintSettings.Pick.FLOOR));
        ModComponents.setMaterial(stack, material);
        return stack;
    }

    /** A Bubie at `at` with a chest at CHEST holding `blocks`, handed the blueprint by a player standing south. */
    private static BubieEntity builder(GameTestHelper helper, BlockPos at, ItemStack plan, ItemStack blocks) {
        helper.setBlock(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
        if (!blocks.isEmpty()) chest.setItem(0, blocks);
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), at);
        bubie.setEnergy(40000);
        bubie.setChest(helper.absolutePos(CHEST));
        Player owner = helper.makeMockSurvivalPlayer();
        BlockPos stand = helper.absolutePos(at.south(6));
        owner.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        BlueprintCompat.handOver(bubie, owner, plan);
        helper.assertTrue(bubie.getJob() == JobType.BUILDER && bubie.getMode() == Mode.WORK, "the Bubie took the blueprint");
        return bubie;
    }

    private static int placed(GameTestHelper helper, BubieEntity bubie, Block block) {
        BlockPos o = bubie.getBuildOrigin();
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-4, 0, -4), o.offset(4, 3, 4))) {
            if (helper.getLevel().getBlockState(p).is(block)) n++;
        }
        return n;
    }

    /**
     * The player builds along: blocks of the plan they place themselves are blocks the Bubie no longer has to, and
     * what the client is told is left (the see-through blocks) is exactly what the Bubie plans.
     */
    static void countsWhatThePlayerPlaced(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        // the chest starts empty, so nothing is built before the player has had their turn
        BubieEntity bubie = builder(helper, new BlockPos(9, 1, 9), blueprint(5, 2, true, Blocks.COBBLESTONE), ItemStack.EMPTY);
        List<BlueprintCompat.Step> plan = BlueprintCompat.plan(bubie);
        int total = plan.size();
        Set<BlockPos> planned = new HashSet<>();
        for (BlueprintCompat.Step s : plan) planned.add(s.pos());
        Set<BlockPos> ghosts = new HashSet<>();
        for (BlueprintCompat.Step s : BlueprintCompat.ghost(bubie)) ghosts.add(s.pos());
        helper.assertTrue(ghosts.equals(planned), "the ghost blocks are the plan: " + ghosts.size() + " ghosts, " + total + " planned");

        int mine = total / 2;
        for (int i = 0; i < mine; i++) helper.getLevel().setBlockAndUpdate(plan.get(i).pos(), plan.get(i).block().defaultBlockState());
        helper.assertTrue(BlueprintCompat.plan(bubie).size() == total - mine, "what the player placed is off the plan");
        helper.assertTrue(BlueprintCompat.ghost(bubie).size() == total - mine, "and off the ghosts");
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, total - mine)); // exactly the rest
        helper.succeedWhen(() -> {
            helper.assertTrue(BlueprintCompat.plan(bubie).isEmpty(), "still " + BlueprintCompat.plan(bubie).size()
                    + " to place; status " + bubie.getStatus());
            helper.assertTrue(placed(helper, bubie, Blocks.COBBLESTONE) == total, "the whole base stands");
            helper.assertTrue(bubie.getStatus() == Status.DONE, "reports done, is " + bubie.getStatus());
        });
    }

    static void buildsFromChest(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        BubieEntity bubie = builder(helper, new BlockPos(9, 1, 9), blueprint(5, 2, true, Blocks.COBBLESTONE),
                new ItemStack(Items.COBBLESTONE, 64));
        int total = BlueprintCompat.plan(bubie).size();
        helper.assertTrue(total > 40, "a 5x5 base with walls is more than 40 blocks, plan has " + total);
        helper.succeedWhen(() -> {
            helper.assertTrue(BlueprintCompat.plan(bubie).isEmpty(), "still " + BlueprintCompat.plan(bubie).size()
                    + " to place; status " + bubie.getStatus());
            helper.assertTrue(placed(helper, bubie, Blocks.COBBLESTONE) == total, "every block of the plan is cobblestone");
            helper.assertTrue(bubie.getStatus() == Status.DONE, "reports done, is " + bubie.getStatus());
            helper.assertTrue(bubie.getBuildTotal() == total && bubie.getBuildLeft() == 0,
                    "progress " + bubie.getBuildLeft() + " left of " + bubie.getBuildTotal());
        });
    }

    static void waitsForBlocks(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        BubieEntity bubie = builder(helper, new BlockPos(9, 1, 9), blueprint(3, 0, true, Blocks.COBBLESTONE),
                new ItemStack(Items.COBBLESTONE, 4));
        boolean[] refilled = {false};
        helper.onEachTick(() -> {
            if (!refilled[0] && bubie.getStatus() == Status.NO_MATERIAL) {
                helper.assertTrue(bubie.getNeed().is(Items.COBBLESTONE) && bubie.getNeed().getCount() == 5,
                        "says it misses 5 cobblestone, says " + bubie.getNeed());
                // the owner (or another Bubie) brings more
                ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(CHEST);
                chest.setItem(5, new ItemStack(Items.COBBLESTONE, 16));
                refilled[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(refilled[0], "it waited for blocks");
            helper.assertTrue(BlueprintCompat.plan(bubie).isEmpty(), "the 3x3 floor is done");
        });
    }

    /** A courier brings the builder only what the plan needs, from a store chest with other things in it. */
    static void courierFeedsTheBuilder(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        BlockPos store = new BlockPos(13, 1, 13);
        helper.setBlock(store, Blocks.CHEST);
        ChestBlockEntity stock = (ChestBlockEntity) helper.getBlockEntity(store);
        stock.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        stock.setItem(1, new ItemStack(Items.DIRT, 20));
        BubieEntity builder = builder(helper, new BlockPos(6, 1, 6), blueprint(3, 0, true, Blocks.COBBLESTONE), ItemStack.EMPTY);
        BubieEntity courier = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(10, 1, 10));
        courier.setEnergy(40000);
        courier.setJob(JobType.COURIER);
        courier.setArea(WorkArea.of(helper.absolutePos(new BlockPos(11, 1, 11)), helper.absolutePos(new BlockPos(15, 2, 15))));
        courier.setChest(helper.absolutePos(CHEST));
        courier.setMode(Mode.WORK);
        helper.succeedWhen(() -> {
            helper.assertTrue(BlueprintCompat.plan(builder).isEmpty(), "the floor is done; builder " + builder.getStatus()
                    + ", courier " + courier.getStatus());
            int dirt = 0, cobble = 0;
            for (int i = 0; i < stock.getContainerSize(); i++) {
                if (stock.getItem(i).is(Items.DIRT)) dirt += stock.getItem(i).getCount();
                if (stock.getItem(i).is(Items.COBBLESTONE)) cobble += stock.getItem(i).getCount();
            }
            helper.assertTrue(dirt == 20, "the dirt stayed in the store");
            helper.assertTrue(cobble >= 64 - 9 - 2, "took about the 9 cobblestone needed, store has " + cobble);
        });
    }

    static void teamwork(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.GRASS_BLOCK);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (BlockPos trunk : new BlockPos[]{new BlockPos(11, 1, 3), new BlockPos(13, 1, 6)}) {
            for (int y = 0; y < 5; y++) helper.setBlock(trunk.above(y), Blocks.OAK_LOG);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = trunk.offset(dx, 5, dz);
                if (helper.getBlockState(p).isAir()) helper.setBlock(p, leaves);
            }
        }
        // the builder waits for logs: a 3x3 outline needs 8, two trees give 10
        BubieEntity builder = builder(helper, new BlockPos(5, 1, 11), blueprint(3, 0, false, Blocks.OAK_LOG), ItemStack.EMPTY);
        BubieEntity lumberjack = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(9, 1, 5));
        lumberjack.setEnergy(40000);
        lumberjack.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        lumberjack.setJob(JobType.LUMBERJACK);
        lumberjack.setArea(WorkArea.of(helper.absolutePos(new BlockPos(9, 1, 1)), helper.absolutePos(new BlockPos(15, 8, 8))));
        lumberjack.setChest(helper.absolutePos(CHEST));
        lumberjack.setMode(Mode.WORK);
        helper.succeedWhen(() -> {
            helper.assertTrue(BlueprintCompat.plan(builder).isEmpty(), "the builder finished with the logs the lumberjack "
                    + "brought; still " + BlueprintCompat.plan(builder).size() + ", builder " + builder.getStatus()
                    + ", lumberjack " + lumberjack.getStatus());
            helper.assertTrue(placed(helper, builder, Blocks.OAK_LOG) == 8, "8 logs placed");
        });
    }
}
