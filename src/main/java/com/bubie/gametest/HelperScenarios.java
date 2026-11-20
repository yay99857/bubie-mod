package com.bubie.gametest;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import com.baseblueprint.api.HelperBuildEvent;
import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import com.bubie.entity.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;

/** Base Blueprint 1.3.0's "Build with Bubie" button; only loaded when its event class exists. */
final class HelperScenarios {
    private HelperScenarios() {
    }

    static void buttonHandsTheBlueprintToTheNearestBubie(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        // a FakePlayer is a ServerPlayer that never logs in (a mock login makes other mods send it packets)
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "BlueprintTester"));
        BlockPos stand = helper.absolutePos(new BlockPos(8, 1, 14));
        player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        BubieEntity near = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(8, 1, 11));
        BubieEntity far = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(2, 1, 2));
        BubieEntity stranger = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(8, 1, 13));
        near.setOwnerUUID(player.getUUID());
        far.setOwnerUUID(player.getUUID());
        stranger.setOwnerUUID(java.util.UUID.randomUUID());

        ItemStack plan = new ItemStack(BaseBlueprint.BLUEPRINT.get());
        plan.set(ModComponents.SETTINGS.get(), new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3,
                BlueprintSettings.Floor.FILLED, 0, false, BlueprintSettings.Roof.NONE, false, BlueprintSettings.Pick.FLOOR));
        plan.set(ModComponents.MATERIAL.get(), Blocks.COBBLESTONE);
        BlockPos origin = helper.absolutePos(new BlockPos(5, 1, 5));
        HelperBuildEvent event = NeoForge.EVENT_BUS.post(new HelperBuildEvent(player, plan, origin));

        helper.assertTrue(event.isAccepted(), "a Bubie took the job");
        helper.assertTrue(near.getJob() == JobType.BUILDER && near.getMode() == Mode.WORK, "the nearest own Bubie builds");
        helper.assertTrue(origin.equals(near.getBuildOrigin()), "at the looked-at spot, not where it stands");
        helper.assertTrue(far.getJob() != JobType.BUILDER && stranger.getJob() != JobType.BUILDER, "the others don't");
        helper.assertTrue(plan.getCount() == 1, "the player keeps the blueprint");
        helper.succeed();
    }
}
