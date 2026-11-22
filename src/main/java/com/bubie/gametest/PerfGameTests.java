package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import com.bubie.entity.Mode;
import com.bubie.entity.WorkArea;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * How much server time Bubies cost: 12 of them digging the same field (3 players' worth with the default limit),
 * measured over 30 s. Prints the average per Bubie per tick; fails above 0.5 ms, a tenth of a whole tick's budget
 * for all twelve together being 6 ms.
 */
@GameTestHolder(Bubie.MOD_ID)
@PrefixGameTestTemplate(false)
public class PerfGameTests {
    private static final int BUBIES = 12, MEASURE = 600, WARMUP = 40;

    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = MEASURE + WARMUP + 100)
    public static void twelveBubiesStayCheap(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        for (int x = 1; x < 15; x++) {
            for (int z = 1; z < 15; z++) {
                for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.SAND);
            }
        }
        helper.setBlock(BubieGameTests.CHEST, Blocks.CHEST);
        WorkArea area = WorkArea.of(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(14, 3, 14)));
        for (int i = 0; i < BUBIES; i++) {
            BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(1 + i, 4, 1 + i % 3 * 6));
            bubie.setEnergy(40000);
            bubie.profiled = true;
            bubie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SHOVEL));
            bubie.setJob(JobType.DIGGER);
            bubie.setArea(area);
            bubie.setChest(helper.absolutePos(BubieGameTests.CHEST));
            bubie.setMode(Mode.WORK);
        }
        helper.runAfterDelay(WARMUP, () -> {
            BubieEntity.profileNanos = 0;
            BubieEntity.profileTicks = 0;
        });
        helper.runAfterDelay(WARMUP + MEASURE, () -> {
            double micros = BubieEntity.profileNanos / 1000.0 / Math.max(1, BubieEntity.profileTicks);
            String report = String.format("Bubie perf: %d Bubie ticks, %.1f us per Bubie per tick, %.2f ms per server tick for %d Bubies",
                    BubieEntity.profileTicks, micros, micros * BUBIES / 1000.0, BUBIES);
            com.mojang.logging.LogUtils.getLogger().info(report);
            helper.assertTrue(micros < 500, report);
            helper.succeed();
        });
    }
}
