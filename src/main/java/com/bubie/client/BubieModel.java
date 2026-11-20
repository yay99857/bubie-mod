package com.bubie.client;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.Mode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The mascot from bubie.gg in blocks, one block tall: a cream CRT monitor for a head (wider than the body, with the
 * tube sticking out the back), a black hoodie with its hood down, and short cream arms and legs.
 * Texture layout: see tools/gen_textures.py.
 */
public class BubieModel extends HierarchicalModel<BubieEntity> implements ArmedModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Bubie.id("bubie"), "main");

    private final ModelPart root, head, torso, rightArm, leftArm, rightLeg, leftLeg;

    public BubieModel(ModelPart root) {
        this.root = root;
        head = root.getChild("head");
        torso = root.getChild("torso");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        PartDefinition head = parts.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -8.0F, -4.0F, 12, 8, 8),
                PartPose.offset(0.0F, 16.0F, 0.0F));
        head.addOrReplaceChild("tube",
                CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -7.0F, 4.0F, 8, 6, 3),
                PartPose.ZERO);
        PartDefinition torso = parts.addOrReplaceChild("torso",
                CubeListBuilder.create().texOffs(0, 26).addBox(-3.0F, -5.0F, -2.0F, 6, 5, 4),
                PartPose.offset(0.0F, 21.0F, 0.0F));
        torso.addOrReplaceChild("hood",
                CubeListBuilder.create().texOffs(24, 26).addBox(-2.5F, -5.0F, 2.0F, 5, 2, 2),
                PartPose.ZERO);
        parts.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(42, 0).addBox(-1.0F, -1.0F, -1.0F, 2, 5, 2),
                PartPose.offset(-4.0F, 17.0F, 0.0F));
        parts.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(42, 8).addBox(-1.0F, -1.0F, -1.0F, 2, 5, 2),
                PartPose.offset(4.0F, 17.0F, 0.0F));
        parts.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(52, 0).addBox(-1.0F, 0.0F, -1.0F, 2, 3, 2),
                PartPose.offset(-1.5F, 21.0F, 0.0F));
        parts.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(52, 6).addBox(-1.0F, 0.0F, -1.0F, 2, 3, 2),
                PartPose.offset(1.5F, 21.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(BubieEntity bubie, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        Face face = bubie.getFace();

        // a heavy head: turns fully, nods only halfway
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5F;
        head.y += Mth.sin(ageInTicks * 0.08F) * 0.25F; // idle bob

        float walk = limbSwing * 0.9F;
        float swing = Math.min(1.0F, limbSwingAmount * 1.4F);
        rightLeg.xRot = Mth.cos(walk) * 1.2F * swing;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.2F * swing;
        rightArm.xRot = Mth.cos(walk + Mth.PI) * 0.9F * swing;
        leftArm.xRot = Mth.cos(walk) * 0.9F * swing;
        rightArm.zRot = 0.08F;
        leftArm.zRot = -0.08F;

        if (bubie.getMode() == Mode.STAY) {
            // sitting: everything drops by a leg's length, legs stick out in front
            for (ModelPart part : new ModelPart[]{head, torso, rightArm, leftArm, rightLeg, leftLeg}) part.y += 3.0F;
            rightLeg.xRot = -Mth.HALF_PI;
            leftLeg.xRot = -Mth.HALF_PI;
            rightLeg.yRot = 0.15F;
            leftLeg.yRot = -0.15F;
            rightArm.xRot = -0.35F;
            leftArm.xRot = -0.35F;
        }

        if (face == Face.SLEEP) {
            head.xRot = 0.3F;
            head.y += 0.4F;
        } else if (face == Face.HAPPY) {
            leftArm.zRot = -2.5F + Mth.sin(ageInTicks * 0.6F) * 0.35F; // wave
            leftArm.xRot = 0.0F;
        }

        if (attackTime > 0.0F) {
            // tool swing
            float t = Mth.sin(Mth.sqrt(attackTime) * Mth.PI);
            rightArm.xRot = -1.9F + t * 1.2F;
            rightArm.yRot = -0.2F;
            torso.yRot = Mth.sin(attackTime * Mth.PI) * 0.15F;
        } else if (!bubie.getMainHandItem().isEmpty() && limbSwingAmount < 0.1F && bubie.getMode() != Mode.STAY) {
            rightArm.xRot = -0.35F; // holding the tool out a little
        }
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack pose) {
        (arm == HumanoidArm.RIGHT ? rightArm : leftArm).translateAndRotate(pose);
    }
}
