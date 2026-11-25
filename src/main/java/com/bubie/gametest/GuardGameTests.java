package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.BubieConfig;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import com.bubie.entity.Status;
import com.bubie.item.ChipType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** The Guard job and the Program Chip that the advanced jobs may need. */
public class GuardGameTests {
    @BubieTest(template = BubieGameTests.FIELD, timeoutTicks = 600)
    public static void guardFightsTheZombieButNotTheCreeper(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        BubieEntity guard = BubieGameTests.bubie(helper, new BlockPos(4, 1, 4), JobType.GUARD, new ItemStack(Items.IRON_SWORD),
                new BlockPos(1, 1, 1), new BlockPos(14, 3, 14));
        Zombie zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(11, 1, 11));
        zombie.setNoAi(true); // stands there: the test is about the Bubie going to it
        Creeper creeper = helper.spawn(net.minecraft.world.entity.EntityTypes.CREEPER, new BlockPos(3, 1, 12));
        creeper.setNoAi(true);
        helper.succeedWhen(() -> {
            helper.assertTrue(!zombie.isAlive(), "the zombie is down; guard " + guard.getStatus() + ", zombie hp " + zombie.getHealth());
            helper.assertTrue(creeper.isAlive() && creeper.getHealth() == creeper.getMaxHealth(), "the creeper is left alone");
            helper.assertTrue(guard.isAlive(), "the guard is fine");
            helper.assertTrue(guard.getTool().getDamageValue() > 0, "the sword was used");
        });
    }

    @BubieTest(template = BubieGameTests.FIELD, timeoutTicks = 200)
    public static void advancedJobsWaitForAProgramChip(GameTestHelper helper) {
        BubieConfig.ADVANCED_JOBS_NEED_CHIP.set(true);
        try {
            BubieGameTests.floor(helper, Blocks.STONE);
            BubieEntity miner = BubieGameTests.bubie(helper, new BlockPos(4, 1, 4), JobType.MINER, new ItemStack(Items.IRON_PICKAXE),
                    new BlockPos(6, 1, 6), new BlockPos(10, 2, 10));
            BubieEntity lumber = BubieGameTests.bubie(helper, new BlockPos(2, 1, 8), JobType.LUMBERJACK, new ItemStack(Items.IRON_AXE),
                    new BlockPos(6, 1, 6), new BlockPos(10, 2, 10));
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(miner.getStatus() == Status.NEEDS_PROGRAM, "the miner waits for the chip, is " + miner.getStatus());
                helper.assertTrue(lumber.getStatus() != Status.NEEDS_PROGRAM, "the lumberjack needs no chip");
                miner.extra().setItem(1, new ItemStack(Bubie.CHIPS.get(ChipType.PROGRAM).get()));
            });
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(miner.getStatus() != Status.NEEDS_PROGRAM, "with the chip it works, is " + miner.getStatus());
                BubieConfig.ADVANCED_JOBS_NEED_CHIP.set(false);
                helper.succeed();
            });
        } catch (RuntimeException e) {
            BubieConfig.ADVANCED_JOBS_NEED_CHIP.set(false);
            throw e;
        }
    }
}
