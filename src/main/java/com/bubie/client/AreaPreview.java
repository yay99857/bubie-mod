package com.bubie.client;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.WorkArea;
import com.bubie.item.ClipboardItem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import javax.annotation.Nullable;

/**
 * Outlines in the world: with a clipboard in hand, the selection (amber), its chest (teal) and home (cream), plus the
 * areas of your Bubies nearby; and for ten seconds after "Show in world" in a Bubie menu, that Bubie area.
 */
@Mod.EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class AreaPreview {
    // Own buffer, flushed right here: Sodium and Iris wrap the shared one and may never draw lines put in it.
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(1 << 16));

    private AreaPreview() {
    }

    @Nullable
    private static ItemStack clipboard(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof ClipboardItem) return stack;
        }
        return null;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        ItemStack clipboard = clipboard(player);
        long now = mc.level.getGameTime();

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        VertexConsumer lines = BUFFERS.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        if (clipboard != null) {
            ClipboardItem.Selection s = ClipboardItem.read(clipboard);
            if (s.dimension().equals(mc.level.dimension().location().toString())) {
                WorkArea area = s.area();
                if (area != null) box(pose, lines, area.box(), 0.96F, 0.75F, 0.41F, 1.0F);
                else if (s.a() != null) box(pose, lines, new AABB(s.a()), 0.96F, 0.75F, 0.41F, 1.0F);
                if (s.chest() != null) box(pose, lines, new AABB(s.chest()).inflate(0.02), 0.36F, 0.75F, 0.75F, 1.0F);
                if (s.home() != null) box(pose, lines, new AABB(s.home()).inflate(0.03), 1.0F, 0.98F, 0.93F, 1.0F);
            }
        }
        for (BubieEntity bubie : mc.level.getEntitiesOfClass(BubieEntity.class, player.getBoundingBox().inflate(64))) {
            boolean shown = bubie.previewUntil > now;
            if (!shown && (clipboard == null || !bubie.isOwnedBy(player))) continue;
            WorkArea area = bubie.getWorkArea();
            float a = shown ? 1.0F : 0.45F;
            if (area != null) box(pose, lines, area.box().inflate(0.01), 0.95F, 0.91F, 0.84F, a);
            BlockPos chest = bubie.getChest();
            if (shown && chest != null) box(pose, lines, new AABB(chest).inflate(0.02), 0.36F, 0.75F, 0.75F, 1.0F);
            BlockPos home = bubie.getHome();
            if (shown && home != null) box(pose, lines, new AABB(home).inflate(0.03), 1.0F, 0.98F, 0.93F, 1.0F);
        }
        pose.popPose();
        BUFFERS.endBatch(RenderType.lines());
    }

    private static void box(PoseStack pose, VertexConsumer lines, AABB box, float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(pose, lines, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
    }
}
