package com.bubie.client;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.Look;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * DEV ONLY: draws a logo square (1024 px on screen) with 3D Bubies over a soft gradient, for ShotDirector to
 * capture and tools/crop_logos.py to cut out. MOD: one Bubie waving with an axe on graphite with an amber glow.
 * PACK: three Bubies on a big grass block on cream.
 */
public class LogoScreen extends Screen {
    public enum Kind { MOD, PACK }

    public static final int SIZE_PX = 1024;
    private final Kind kind;
    private Identifier background;
    private static int nextId = -1_000_000;

    public LogoScreen(Kind kind) {
        super(Component.literal("logo"));
        this.kind = kind;
    }

    private static BubieEntity bubie(Look look, Face face, ItemStack hand, int age) {
        BubieEntity b = Bubie.BUBIE_ENTITY.get().create(Minecraft.getInstance().level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
        b.setId(--nextId); // never added to the world, but rendering asks every entity for its id
        b.setLook(look);
        b.setDisplayFace(face);
        b.setItemSlot(EquipmentSlot.MAINHAND, hand);
        b.tickCount = age;
        return b;
    }

    /** A radial gradient, painted once into a texture the size of the square. */
    private Identifier gradient(int inner, int outer, float cx, float cy, float radius) {
        NativeImage img = new NativeImage(SIZE_PX, SIZE_PX, false);
        for (int y = 0; y < SIZE_PX; y++) {
            for (int x = 0; x < SIZE_PX; x++) {
                float d = Mth.clamp((float) Math.hypot(x - cx * SIZE_PX, y - cy * SIZE_PX) / (radius * SIZE_PX), 0, 1);
                float t = d * d * (3 - 2 * d);
                int r = (int) Mth.lerp(t, inner >> 16 & 255, outer >> 16 & 255);
                int g = (int) Mth.lerp(t, inner >> 8 & 255, outer >> 8 & 255);
                int bl = (int) Mth.lerp(t, inner & 255, outer & 255);
                img.setPixelABGR(x, y, 0xFF000000 | bl << 16 | g << 8 | r);
            }
        }
        Identifier id = Bubie.id("logo/" + kind.name().toLowerCase());
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "bubie logo", img));
        return id;
    }

    @Override
    protected void init() {
        if (background == null) {
            background = kind == Kind.MOD
                    ? gradient(0x3A2A1C, 0x141116, 0.5F, 0.45F, 0.75F)
                    : gradient(0xFFF4DD, 0xEAD9BE, 0.5F, 0.4F, 0.8F);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        double scale = minecraft.getWindow().getGuiScale();
        float side = (float) (SIZE_PX / scale);
        float x0 = (width - side) / 2F, y0 = (height - side) / 2F;
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, background, (int) x0, (int) y0, 0, 0,
                (int) side, (int) side, SIZE_PX, SIZE_PX, SIZE_PX, SIZE_PX);
        g.enableScissor((int) x0, (int) y0, (int) (x0 + side), (int) (y0 + side));
        if (kind == Kind.MOD) {
            BubieEntity hero = bubie(Look.DEFAULT, Face.HAPPY, ItemStack.EMPTY, 7);
            draw(g, hero, x0 + side * 0.5F, y0 + side * 0.92F, side * 0.80F, -28F, 8F);
        } else {
            // three little islands: two behind, the main one in front
            float back = side * 0.30F, front = side * 0.44F;
            grass(g, x0 + side * 0.25F, y0 + side * 0.40F, back);
            draw(g, bubie(new Look(1, 3, 2, 6, 1), Face.IDLE, ItemStack.EMPTY, 30), x0 + side * 0.25F, y0 + side * 0.495F, side * 0.25F, -12F, 6F);
            grass(g, x0 + side * 0.75F, y0 + side * 0.40F, back);
            draw(g, bubie(new Look(4, 10, 3, 7, 7), Face.IDLE, ItemStack.EMPTY, 52), x0 + side * 0.75F, y0 + side * 0.495F, side * 0.25F, 12F, 6F);
            grass(g, x0 + side * 0.5F, y0 + side * 0.53F, front);
            draw(g, bubie(Look.DEFAULT, Face.HAPPY, ItemStack.EMPTY, 7), x0 + side * 0.5F, y0 + side * 0.665F, side * 0.36F, -16F, 6F);
        }
        g.disableScissor();
    }

    /** A grass block item drawn big: centre x, top y, width. */
    private static void grass(GuiGraphicsExtractor g, float cx, float top, float size) {
        g.pose().pushMatrix();
        g.pose().translate(cx - size / 2F, top);
        g.pose().scale(size / 16F, size / 16F);
        g.item(new ItemStack(Items.GRASS_BLOCK), 0, 0);
        g.pose().popMatrix();
    }

    /** One Bubie: feet at (x, y), height h, turned yaw degrees, camera looking down pitch degrees. */
    private static void draw(GuiGraphicsExtractor g, BubieEntity b, float x, float y, float h, float yaw, float pitch) {
        var state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(b).createRenderState(b, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
            living.bodyRot = 180F + yaw;
            living.yRot = 0F;
            living.xRot = 0F;
        }
        Quaternionf pose = new Quaternionf().rotateZ(Mth.PI).rotateX(-pitch * Mth.DEG_TO_RAD);
        Quaternionf camera = new Quaternionf().rotateX(-pitch * Mth.DEG_TO_RAD);
        // a square picture around the Bubie, its feet at (x, y): the picture's origin is its centre
        int half = Math.round(h);
        g.entity(state, h, new Vector3f(0, half / h, 0), pose, camera, Math.round(x) - half, Math.round(y) - 2 * half,
                Math.round(x) + half, Math.round(y));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
