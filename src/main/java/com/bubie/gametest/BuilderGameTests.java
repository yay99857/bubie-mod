package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.compat.BlueprintCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Builder with Base Blueprint. Passes at once when Base Blueprint isn't installed. */
@GameTestHolder(Bubie.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuilderGameTests {
    private static boolean skip(GameTestHelper helper) {
        if (BlueprintCompat.loaded()) return false;
        helper.succeed();
        return true;
    }

    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 2400)
    public static void builderBuildsABaseFromItsChest(GameTestHelper helper) {
        if (!skip(helper)) BuilderScenarios.buildsFromChest(helper);
    }

    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 2400)
    public static void builderWaitsForBlocksThenFinishes(GameTestHelper helper) {
        if (!skip(helper)) BuilderScenarios.waitsForBlocks(helper);
    }

    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 100)
    public static void buildWithBubieButtonPicksTheNearestOwnBubie(GameTestHelper helper) {
        if (skip(helper)) return;
        try {
            Class.forName("com.baseblueprint.api.HelperBuildEvent");
        } catch (ClassNotFoundException e) {
            helper.succeed(); // Base Blueprint older than 1.3.0: no button
            return;
        }
        HelperScenarios.buttonHandsTheBlueprintToTheNearestBubie(helper);
    }

    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 2400)
    public static void courierBringsTheBuilderOnlyWhatItNeeds(GameTestHelper helper) {
        if (!skip(helper)) BuilderScenarios.courierFeedsTheBuilder(helper);
    }

    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 3600)
    public static void lumberjackFeedsTheBuilder(GameTestHelper helper) {
        if (!skip(helper)) BuilderScenarios.teamwork(helper);
    }
}
