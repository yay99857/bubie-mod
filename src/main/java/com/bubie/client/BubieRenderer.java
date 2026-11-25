package com.bubie.client;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.Status;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.HumanoidArm;


public class BubieRenderer extends MobRenderer<BubieEntity, BubieRenderState, BubieModel> {

    public BubieRenderer(EntityRendererProvider.Context context) {
        super(context, new BubieModel(context.bakeLayer(BubieModel.LAYER)), 0.35F);
        addLayer(new FaceLayer(this));
        addLayer(new ToolLayer(this));
    }

    @Override
    public Identifier getTextureLocation(BubieRenderState state) {
        return LookTextures.body(state.look);
    }

    @Override
    public BubieRenderState createRenderState() {
        return new BubieRenderState();
    }

    @Override
    public void extractRenderState(BubieEntity bubie, BubieRenderState state, float partialTick) {
        super.extractRenderState(bubie, state, partialTick);
        ArmedEntityRenderState.extractArmedEntityRenderState(bubie, state, itemModelResolver, partialTick);
        state.look = bubie.getLook();
        state.mode = bubie.getMode();
        state.face = bubie.getFace();

        boolean editing = bubie.previewLook != null;
        Face face = editing ? Face.IDLE : state.face;
        // blink every few seconds
        boolean blink = !editing && face == Face.IDLE && (bubie.tickCount + bubie.getId() * 7) % 90 < 3;
        state.faceTexture = face == Face.MUSIC
                ? LookTextures.music(state.look, (bubie.tickCount + bubie.getId()) / 3)
                : LookTextures.face(state.look, face, blink);

        // a balloon with the problem, so you know why it stopped without opening the menu; else the song it plays
        state.balloon = null;
        Status status = bubie.getStatus();
        if (status.problem && state.distanceToCameraSq < 24 * 24) {
            state.balloon = Component.literal("⚠ ").append(bubie.statusText()).withStyle(ChatFormatting.GOLD);
        } else if (state.distanceToCameraSq < 16 * 16) {
            com.bubie.entity.Dj.Track track = bubie.getDjTrack();
            if (!track.isNone()) state.balloon = Component.literal("♪ " + track.title()).withStyle(ChatFormatting.AQUA);
        }
        state.balloonAttachment = state.balloon == null ? null
                : bubie.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, bubie.getYRot(partialTick));
    }

    @Override
    public void submit(BubieRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        if (state.balloon == null || state.balloonAttachment == null) return;
        pose.pushPose();
        if (state.nameTag != null) pose.translate(0.0, 0.28, 0.0);
        collector.submitNameTag(pose, state.balloonAttachment, 0, state.balloon, !state.isDiscrete, state.lightCoords, camera);
        pose.popPose();
    }

    /** The face on the screen glows (an emissive layer), so you can see your Bubie at night. */
    static class FaceLayer extends RenderLayer<BubieRenderState, BubieModel> {
        FaceLayer(RenderLayerParent<BubieRenderState, BubieModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, BubieRenderState state, float yRot, float xRot) {
            if (state.isInvisible || state.faceTexture == null) return;
            collector.order(1).submitModel(getParentModel(), state, pose, RenderTypes.eyes(state.faceTexture), 0xF00000,
                    OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
    }

    /** The tool in its right hand, scaled to its little arm. */
    static class ToolLayer extends RenderLayer<BubieRenderState, BubieModel> {
        ToolLayer(RenderLayerParent<BubieRenderState, BubieModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, BubieRenderState state, float yRot, float xRot) {
            if (state.rightHandItemState.isEmpty()) return;
            pose.pushPose();
            getParentModel().translateToHand(state, HumanoidArm.RIGHT, pose);
            pose.translate(0.0F, 0.2F, 0.0F);
            pose.rotateDegrees(Axis.XP, -90.0F);
            pose.rotateDegrees(Axis.YP, 180.0F);
            pose.scale(0.62F, 0.62F, 0.62F);
            pose.translate(-1.0F / 16.0F, 0.05F, -0.12F);
            state.rightHandItemState.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }
    }
}
