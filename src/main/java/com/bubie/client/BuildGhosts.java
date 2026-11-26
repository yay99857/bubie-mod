package com.bubie.client;

import com.bubie.Bubie;
import com.bubie.BubieClientConfig;
import com.bubie.compat.BlueprintCompat;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * What a Bubie Builder still has to place, drawn in the world as see-through blocks. Aim at one and it says which
 * block goes there, so the player can build along: the Bubie plans against the world, so a block the player places
 * is one it no longer has to. Needs Base Blueprint 1.4+.
 */
@Mod.EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
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
    // Our own buffer, flushed right here: Sodium and Iris wrap the shared one and may never draw what is put in it.
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(1 << 18));

    private BuildGhosts() {
    }

    @SubscribeEvent
    static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
    }

    /** The first ghost on the line of sight, within reach and before whatever real block or entity is aimed at. */
    @Nullable
    private static BlockPos lookedAt(Minecraft mc, LocalPlayer player) {
        Vec3 from = player.getEyePosition();
        Vec3 dir = player.getViewVector(1.0F);
        double reach = player.getBlockReach();
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

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Map<BlockPos, Ghost> all = ghosts;
        Minecraft mc = Minecraft.getInstance();
        if (all.isEmpty() || mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        RenderType sheet = Sheets.translucentCullBlockSheet();
        VertexConsumer faded = new Faded(BUFFERS.getBuffer(sheet), ALPHA);
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        for (Map.Entry<BlockPos, Ghost> entry : all.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.distToCenterSqr(cam) > DRAW * DRAW) continue;
            if (!mc.level.getBlockState(pos).canBeReplaced()) continue; // placed since the last look at the plan
            BlockState state = entry.getValue().block().defaultBlockState();
            pose.pushPose();
            pose.translate(pos.getX() - cam.x + 0.5, pos.getY() - cam.y + 0.5, pos.getZ() - cam.z + 0.5);
            pose.scale(0.996F, 0.996F, 0.996F); // a hair smaller than a block, so its faces don't fight the neighbours'
            pose.translate(-0.5, -0.5, -0.5);
            blocks.getModelRenderer().renderModel(pose.last(), faded, state, blocks.getBlockModel(state), 1.0F, 1.0F, 1.0F,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }
        BUFFERS.endBatch(sheet);

        BlockPos aim = hovered;
        if (aim != null && all.containsKey(aim)) {
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            LevelRenderer.renderLineBox(pose, BUFFERS.getBuffer(RenderType.lines()), aim.getX(), aim.getY(), aim.getZ(),
                    aim.getX() + 1, aim.getY() + 1, aim.getZ() + 1, 0.96F, 0.75F, 0.41F, 1.0F);
            pose.popPose();
            BUFFERS.endBatch(RenderType.lines());
        }
    }

    /** Over the crosshair: which block goes in the ghost you aim at, and whose base it is. */
    @SubscribeEvent
    static void onHud(RenderGuiEvent.Post event) {
        BlockPos aim = hovered;
        Ghost ghost = aim == null ? null : ghosts.get(aim);
        Minecraft mc = Minecraft.getInstance();
        if (ghost == null || mc.screen != null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        Component text = Component.translatable("bubie.ghost.place", ghost.block().getName(), ghost.bubie());
        g.drawCenteredString(mc.font, text, g.guiWidth() / 2, g.guiHeight() / 2 - 24, 0xFFF4BF69);
    }

    /** Passes vertices on with their alpha scaled down, which is all it takes to turn a block model into a ghost. */
    private record Faded(VertexConsumer to, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            to.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int a) {
            to.color(red, green, blue, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            to.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            to.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            to.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            to.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            to.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int a) {
            to.defaultColor(red, green, blue, Math.round(a * alpha));
        }

        @Override
        public void unsetDefaultColor() {
            to.unsetDefaultColor();
        }
    }
}
