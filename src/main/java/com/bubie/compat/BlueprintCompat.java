package com.bubie.compat;

import com.baseblueprint.BaseBuilder;
import com.baseblueprint.BlueprintSettings;
import com.baseblueprint.ModComponents;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.JobType;
import com.bubie.entity.Mode;
import com.bubie.job.BlockBreaker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Base Blueprint, when present: a Bubie given a blueprint builds that base block by block. Only this class touches
 * Base Blueprint's code, and only after {@link #loaded()} says the mod is there.
 */
public final class BlueprintCompat {
    public static final String MOD_ID = "baseblueprint";

    /** One block of the plan, in the order Base Blueprint builds (foundation up, then floor, walls, roof). */
    public record Step(BlockPos pos, Block block) {
    }

    private BlueprintCompat() {
    }

    public static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** By id, so Bubie never loads Base Blueprint classes when it isn't installed. */
    public static boolean isBlueprint(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(MOD_ID + ":base_blueprint");
    }

    /** The player hands a blueprint to the Bubie: it will build that base around where it stands. */
    public static void handOver(BubieEntity bubie, Player player, ItemStack stack) {
        player.displayClientMessage(handOver(bubie, player, stack, bubie.blockPosition()), true);
    }

    /** Hands the blueprint over to build at `origin`; returns the message for the player. */
    public static Component handOver(BubieEntity bubie, Player player, ItemStack stack, BlockPos origin) {
        BlueprintSettings settings = BaseBuilder.settings(stack).clamped();
        BaseBuilder.Materials materials = BaseBuilder.materials(player, stack);
        if (materials.floor() == null) {
            return Component.translatable("bubie.builder.no_material").withStyle(ChatFormatting.YELLOW);
        }
        Direction door = BaseBuilder.doorSide(settings, origin, player);
        ItemStack plan = stack.copyWithCount(1);
        // the materials in force now (an off-hand block wins in Base Blueprint) are written into Bubie's copy
        ModComponents.setMaterial(plan, materials.floor());
        if (materials.walls() != null) ModComponents.setWallMaterial(plan, materials.walls());
        bubie.setBuildPlan(plan, origin, door);
        bubie.setJob(JobType.BUILDER);
        bubie.setMode(Mode.WORK);
        int blocks = plan(bubie).size();
        bubie.setBuildTotal(blocks);
        bubie.showMood(Face.HAPPY, 60);
        bubie.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.2F);
        return Component.translatable("bubie.builder.accepted", bubie.getDisplayName(), blocks,
                materials.floor().getName()).withStyle(ChatFormatting.GREEN);
    }

    /** What is still left to place, as Base Blueprint plans it, from the Bubie's copy of the blueprint. */
    public static List<Step> plan(BubieEntity bubie) {
        ItemStack stack = bubie.getBuildPlan();
        BlockPos origin = bubie.getBuildOrigin();
        if (stack.isEmpty() || origin == null) return List.of();
        BlueprintSettings settings = BaseBuilder.settings(stack).clamped();
        // A fake player standing out past the chosen side makes Base Blueprint put the door there, and makes claim
        // checks (mayInteract) ask about the owner.
        FakePlayer player = BlockBreaker.player(bubie, ItemStack.EMPTY);
        Direction door = bubie.getBuildDoor();
        player.setPos(origin.getX() + 0.5 + door.getStepX() * 64, origin.getY(), origin.getZ() + 0.5 + door.getStepZ() * 64);
        BaseBuilder.Materials materials = BaseBuilder.materials(player, stack);
        if (materials.floor() == null) return List.of();
        BaseBuilder.Plan plan = BaseBuilder.plan(bubie.level(), player, settings, origin, materials);
        List<Step> steps = new ArrayList<>(plan.toPlace().size());
        for (BaseBuilder.Placement p : plan.toPlace()) steps.add(new Step(p.pos(), p.block()));
        return steps;
    }

    @Nullable
    private static Boolean ghostApi;

    /** Base Blueprint 1.4.0+: a plan can be asked for with the door on a given side, without a player. */
    private static boolean ghostApi() {
        if (ghostApi == null) {
            try {
                BaseBuilder.class.getMethod("plan", Level.class, Player.class, BlueprintSettings.class,
                        BlockPos.class, BaseBuilder.Materials.class, Direction.class);
                ghostApi = true;
            } catch (NoSuchMethodException | LinkageError e) {
                ghostApi = false;
            }
        }
        return ghostApi;
    }

    /**
     * What is still left to place, worked out from the Bubie's synced plan alone (no fake player): what the client
     * draws as see-through blocks. Empty with a Base Blueprint older than 1.4.0.
     */
    public static List<Step> ghost(BubieEntity bubie) {
        ItemStack stack = bubie.getBuildPlan();
        BlockPos origin = bubie.getBuildOrigin();
        if (stack.isEmpty() || origin == null || !ghostApi()) return List.of();
        Block floor = ModComponents.getMaterial(stack);
        if (floor == null) return List.of();
        Block walls = ModComponents.getWallMaterial(stack);
        BaseBuilder.Plan plan = BaseBuilder.plan(bubie.level(), null, BaseBuilder.settings(stack).clamped(), origin,
                new BaseBuilder.Materials(floor, walls == null ? floor : walls), bubie.getBuildDoor());
        List<Step> steps = new ArrayList<>(plan.toPlace().size());
        for (BaseBuilder.Placement p : plan.toPlace()) steps.add(new Step(p.pos(), p.block()));
        return steps;
    }
}
