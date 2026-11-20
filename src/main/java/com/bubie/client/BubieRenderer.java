package com.bubie.client;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.Status;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;


public class BubieRenderer extends MobRenderer<BubieEntity, BubieModel> {

    public BubieRenderer(EntityRendererProvider.Context context) {
        super(context, new BubieModel(context.bakeLayer(BubieModel.LAYER)), 0.35F);
        addLayer(new FaceLayer(this));
        addLayer(new ToolLayer(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(BubieEntity bubie) {
        return LookTextures.body(bubie.getLook());
    }

    @Override
    public void render(BubieEntity bubie, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(bubie, yaw, partialTick, pose, buffers, light);
        Status status = bubie.getStatus();
        if (status.problem && entityRenderDispatcher.distanceToSqr(bubie) < 24 * 24) {
            // a balloon with the problem, so you know why it stopped without opening the menu
            pose.pushPose();
            if (shouldShowName(bubie)) pose.translate(0.0, 0.28, 0.0);
            Component text = Component.literal("⚠ ").append(bubie.statusText()).withStyle(ChatFormatting.GOLD);
            renderNameTag(bubie, text, pose, buffers, light, partialTick);
            pose.popPose();
        } else if (entityRenderDispatcher.distanceToSqr(bubie) < 16 * 16) {
            com.bubie.entity.Dj.Track track = bubie.getDjTrack();
            if (!track.isNone()) {
                pose.pushPose();
                if (shouldShowName(bubie)) pose.translate(0.0, 0.28, 0.0);
                renderNameTag(bubie, Component.literal("♪ " + track.title()).withStyle(ChatFormatting.AQUA), pose, buffers, light, partialTick);
                pose.popPose();
            }
        }
    }

    /** The face on the screen glows (an emissive layer), so you can see your Bubie at night. */
    static class FaceLayer extends RenderLayer<BubieEntity, BubieModel> {
        FaceLayer(RenderLayerParent<BubieEntity, BubieModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, BubieEntity bubie, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (bubie.isInvisible()) return;
            boolean editing = bubie.previewLook != null;
            Face face = editing ? Face.IDLE : bubie.getFace();
            // blink every few seconds
            boolean blink = !editing && face == Face.IDLE && (bubie.tickCount + bubie.getId() * 7) % 90 < 3;
            ResourceLocation texture = face == Face.MUSIC
                    ? LookTextures.music(bubie.getLook(), (bubie.tickCount + bubie.getId()) / 3)
                    : LookTextures.face(bubie.getLook(), face, blink);
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(texture)), 0xF00000,
                    OverlayTexture.NO_OVERLAY);
        }
    }

    /** The tool in its right hand, scaled to its little arm. */
    static class ToolLayer extends RenderLayer<BubieEntity, BubieModel> {
        private final ItemInHandRenderer items;

        ToolLayer(RenderLayerParent<BubieEntity, BubieModel> parent, ItemInHandRenderer items) {
            super(parent);
            this.items = items;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, BubieEntity bubie, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack stack = bubie.getMainHandItem();
            if (stack.isEmpty()) return;
            pose.pushPose();
            getParentModel().translateToHand(HumanoidArm.RIGHT, pose);
            pose.translate(0.0F, 0.2F, 0.0F);
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.scale(0.62F, 0.62F, 0.62F);
            pose.translate(-1.0F / 16.0F, 0.05F, -0.12F);
            items.renderItem(bubie, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffers, light);
            pose.popPose();
        }
    }
}
