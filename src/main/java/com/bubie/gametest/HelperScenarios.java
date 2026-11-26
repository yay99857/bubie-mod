package com.bubie.gametest;

import com.baseblueprint.BaseBlueprint;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import com.baseblueprint.api.HelperBuildEvent;
import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import com.bubie.entity.Mode;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.UUID;

/** Base Blueprint asking for a helper to build; only loaded when its event class exists (1.3.0+). */
final class HelperScenarios {
    private HelperScenarios() {
    }

    static void buttonHandsTheBlueprintToTheNearestBubie(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        // a FakePlayer is a ServerPlayer that never logs in (a mock login makes other mods send it packets)
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "BlueprintTester"));
        BlockPos stand = helper.absolutePos(new BlockPos(8, 1, 14));
        player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        BubieEntity near = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(8, 1, 11));
        BubieEntity far = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(2, 1, 2));
        BubieEntity stranger = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(8, 1, 13));
        near.setOwnerUUID(player.getUUID());
        far.setOwnerUUID(player.getUUID());
        stranger.setOwnerUUID(UUID.randomUUID());

        ItemStack plan = new ItemStack(BaseBlueprint.BLUEPRINT.get());
        ModComponents.setSettings(plan, new BlueprintSettings(BlueprintSettings.Shape.SQUARE, 3, 3,
                BlueprintSettings.Floor.FILLED, 0, false, BlueprintSettings.Roof.NONE, false, BlueprintSettings.Pick.FLOOR));
        ModComponents.setMaterial(plan, Blocks.COBBLESTONE);
        BlockPos origin = helper.absolutePos(new BlockPos(5, 1, 5));
        HelperBuildEvent event = new HelperBuildEvent(player, plan, origin);
        MinecraftForge.EVENT_BUS.post(event);

        helper.assertTrue(event.isAccepted(), "a Bubie took the job");
        helper.assertTrue(near.getJob() == JobType.BUILDER && near.getMode() == Mode.WORK, "the nearest own Bubie builds");
        helper.assertTrue(origin.equals(near.getBuildOrigin()), "at the looked-at spot, not where it stands");
        helper.assertTrue(far.getJob() != JobType.BUILDER && stranger.getJob() != JobType.BUILDER, "the others don't");
        helper.assertTrue(plan.getCount() == 1, "the player keeps the blueprint");
        helper.succeed();
    }

    /**
     * Base Blueprint 1.4.0: with "Built by: Bubie" set in the menu, right-clicking a block (the same click that shows
     * the preview) hands the base to the Bubie, on that block, and places nothing at once.
     */
    static void clickingABlockHandsTheBaseToTheBubie(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "BlueprintClicker"));
        BlockPos stand = helper.absolutePos(new BlockPos(8, 1, 14));
        player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        BubieEntity mine = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(8, 1, 11));
        mine.setOwnerUUID(player.getUUID());

        // the settings as the menu saves them, through their codec (so this also compiles against Base Blueprint 1.3)
        JsonObject json = BlueprintSettings.CODEC.encodeStart(JsonOps.INSTANCE, new BlueprintSettings(
                BlueprintSettings.Shape.SQUARE, 3, 3, BlueprintSettings.Floor.FILLED, 0, false, BlueprintSettings.Roof.NONE,
                false, BlueprintSettings.Pick.FLOOR)).getOrThrow(false, msg -> { throw new RuntimeException(msg); }).getAsJsonObject();
        json.addProperty("helper", true);
        ItemStack plan = new ItemStack(BaseBlueprint.BLUEPRINT.get());
        ModComponents.setSettings(plan, BlueprintSettings.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(false, msg -> { throw new RuntimeException(msg); }));
        ModComponents.setMaterial(plan, Blocks.COBBLESTONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, plan);

        BlockPos clicked = helper.absolutePos(new BlockPos(5, 0, 5)); // the top of a floor block
        plan.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0),
                        Direction.UP, clicked, false)));

        helper.assertTrue(mine.getJob() == JobType.BUILDER && mine.getMode() == Mode.WORK, "the Bubie builds; job " + mine.getJob());
        helper.assertTrue(clicked.above().equals(mine.getBuildOrigin()), "on the clicked block, got " + mine.getBuildOrigin());
        helper.assertTrue(helper.getLevel().getBlockState(clicked.above()).isAir(), "nothing is placed at once");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }
}
