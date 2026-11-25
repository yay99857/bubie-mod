package com.bubie.client;

import com.bubie.Bubie;
import com.bubie.BubieClientConfig;
import com.bubie.compat.BlueprintCompat;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * What a Bubie Builder still has to place, drawn in the world as see-through blocks. Aim at one and it says which
 * block goes there, so the player can build along: the Bubie plans against the world, so a block the player places
 * is one it no longer has to. Needs Base Blueprint 1.4+.
 */
@EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class BuildGhosts {
    private static final int REFRESH_TICKS = 10, MAX_GHOSTS = 3000;
    private static final double SEE = 64, DRAW = 48;
    private static final float ALPHA = 0.3F;

    /** One block still to place, and the Bubie building it. */
    private record Ghost(Block block, Component bubie) {
    }

    private static Map<BlockPos, Ghost> ghosts = Map.of();
    @Nullable
    private static BlockPos hovered;
    private static int tick;

    private BuildGhosts() {
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !BlueprintCompat.loaded() || !BubieClientConfig.BUILD_GHOSTS.get()) {
            ghosts = Map.of();
            hovered = null;
            return;
        }
        if (tick++ % REFRESH_TICKS == 0) {
            Map<BlockPos, Ghost> next = new HashMap<>();
            for (BubieEntity bubie : mc.level.getEntitiesOfClass(BubieEntity.class, player.getBoundingBox().inflate(SEE))) {
                if (bubie.getJob() != JobType.BUILDER || bubie.getBuildPlan().isEmpty()) continue;
                for (BlueprintCompat.Step step : BlueprintCompat.ghost(bubie)) {
                    if (next.size() >= MAX_GHOSTS) break;
                    next.putIfAbsent(step.pos(), new Ghost(step.block(), bubie.getDisplayName()));
                }
            }
            ghosts = next;
        }
        hovered = ghosts.isEmpty() ? null : lookedAt(mc, player);
        draw(mc, player);
    }

    /** The first ghost on the line of sight, within reach and before whatever real block or entity is aimed at. */
    @Nullable
    private static BlockPos lookedAt(Minecraft mc, LocalPlayer player) {
        Vec3 from = player.getEyePosition();
        Vec3 dir = player.getViewVector(1.0F);
        double reach = player.blockInteractionRange();
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
            reach = Math.min(reach, mc.hitResult.getLocation().distanceTo(from));
        }
        BlockPos last = null;
        for (double t = 0; t <= reach; t += 0.05) {
            BlockPos pos = BlockPos.containing(from.x + dir.x * t, from.y + dir.y * t, from.z + dir.z * t);
            if (pos.equals(last)) continue;
            last = pos;
            if (ghosts.containsKey(pos) && mc.level.getBlockState(pos).canBeReplaced()) return pos;
        }
        return null;
    }

    /** Every tick, as gizmos (the game draws them each frame until the next tick): see-through blocks in the colour of each one. */
    private static void draw(Minecraft mc, LocalPlayer player) {
        Map<BlockPos, Ghost> all = ghosts;
        if (all.isEmpty() || mc.level == null) return;
        Vec3 eye = player.getEyePosition();
        int alpha = Math.round(ALPHA * 255);
        for (Map.Entry<BlockPos, Ghost> entry : all.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.distToCenterSqr(eye) > DRAW * DRAW) continue;
            if (!mc.level.getBlockState(pos).canBeReplaced()) continue; // placed since the last look at the plan
            int rgb = entry.getValue().block().defaultMapColor().col;
            // a hair smaller than a block, so its faces don't fight the neighbours'
            Gizmos.cuboid(new AABB(pos).deflate(0.002), GizmoStyle.fill(ARGB.color(alpha, rgb)));
        }
        BlockPos aim = hovered;
        if (aim != null && all.containsKey(aim)) {
            Gizmos.cuboid(new AABB(aim), GizmoStyle.stroke(ARGB.colorFromFloat(1.0F, 0.96F, 0.75F, 0.41F), 2.0F));
        }
    }

    /** Over the crosshair: which block goes in the ghost you aim at, and whose base it is. */
    @SubscribeEvent
    static void onHud(RenderGuiEvent.Post event) {
        BlockPos aim = hovered;
        Ghost ghost = aim == null ? null : ghosts.get(aim);
        Minecraft mc = Minecraft.getInstance();
        if (ghost == null || mc.gui.screen() != null || mc.gui.hud.isHidden()) return;
        GuiGraphicsExtractor g = event.getGuiGraphics();
        Component text = Component.translatable("bubie.ghost.place", ghost.block().getName(), ghost.bubie());
        g.centeredText(mc.font, text, g.guiWidth() / 2, g.guiHeight() / 2 - 24, 0xFFF4BF69);
    }
}
