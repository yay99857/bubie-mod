package com.bubie.client;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.Look;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    private ResourceLocation background;

    public LogoScreen(Kind kind) {
        super(Component.literal("logo"));
        this.kind = kind;
    }

    private static BubieEntity bubie(Look look, Face face, ItemStack hand, int age) {
        BubieEntity b = Bubie.BUBIE_ENTITY.get().create(Minecraft.getInstance().level);
        b.setLook(look);
        b.setDisplayFace(face);
        b.setItemSlot(EquipmentSlot.MAINHAND, hand);
        b.tickCount = age;
        return b;
    }

    /** A radial gradient, painted once into a texture the size of the square. */
    private ResourceLocation gradient(int inner, int outer, float cx, float cy, float radius) {
        NativeImage img = new NativeImage(SIZE_PX, SIZE_PX, false);
        for (int y = 0; y < SIZE_PX; y++) {
            for (int x = 0; x < SIZE_PX; x++) {
                float d = Mth.clamp((float) Math.hypot(x - cx * SIZE_PX, y - cy * SIZE_PX) / (radius * SIZE_PX), 0, 1);
                float t = d * d * (3 - 2 * d);
                int r = (int) Mth.lerp(t, inner >> 16 & 255, outer >> 16 & 255);
                int g = (int) Mth.lerp(t, inner >> 8 & 255, outer >> 8 & 255);
                int bl = (int) Mth.lerp(t, inner & 255, outer & 255);
                img.setPixelRGBA(x, y, 0xFF000000 | bl << 16 | g << 8 | r);
            }
        }
        ResourceLocation id = Bubie.id("logo/" + kind.name().toLowerCase());
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
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
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        double scale = minecraft.getWindow().getGuiScale();
        float side = (float) (SIZE_PX / scale);
        float x0 = (width - side) / 2F, y0 = (height - side) / 2F;
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.blit(background, 0, 0, (int) side, (int) side, 0, 0, SIZE_PX, SIZE_PX, SIZE_PX, SIZE_PX);
        g.pose().popPose();
        g.enableScissor((int) x0, (int) y0, (int) (x0 + side), (int) (y0 + side));
        if (kind == Kind.MOD) {
            BubieEntity hero = bubie(Look.DEFAULT, Face.HAPPY, ItemStack.EMPTY, 7);
            draw(g, hero, x0 + side * 0.5F, y0 + side * 0.92F, side * 0.80F, -28F, 8F);
        } else {
            // three little islands: two behind, the main one in front
            float back = side * 0.30F, front = side * 0.44F;
            grass(g, x0 + side * 0.25F, y0 + side * 0.40F, back);
            draw(g, bubie(new Look(1, 3, 2, 6, 1), Face.IDLE, ItemStack.EMPTY, 30), x0 + side * 0.25F, y0 + side * 0.495F, side * 0.25F, -12F, 6F);
            clearDepth(g);
            grass(g, x0 + side * 0.75F, y0 + side * 0.40F, back);
            draw(g, bubie(new Look(4, 10, 3, 7, 7), Face.IDLE, ItemStack.EMPTY, 52), x0 + side * 0.75F, y0 + side * 0.495F, side * 0.25F, 12F, 6F);
            clearDepth(g);
            grass(g, x0 + side * 0.5F, y0 + side * 0.53F, front);
            draw(g, bubie(Look.DEFAULT, Face.HAPPY, ItemStack.EMPTY, 7), x0 + side * 0.5F, y0 + side * 0.665F, side * 0.36F, -16F, 6F);
        }
        g.disableScissor();
    }

    /** A grass block item drawn big: centre x, top y, width. */
    private static void grass(GuiGraphics g, float cx, float top, float size) {
        g.pose().pushPose();
        g.pose().translate(cx - size / 2F, top, 100);
        g.pose().scale(size / 16F, size / 16F, size / 16F);
        g.renderItem(new ItemStack(Items.GRASS_BLOCK), 0, 0);
        g.pose().popPose();
        clearDepth(g);
    }

    private static void clearDepth(GuiGraphics g) {
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(256, Minecraft.ON_OSX);
    }

    /** One Bubie: feet at (x, y), height h, turned yaw degrees, camera looking down pitch degrees. */
    private static void draw(GuiGraphics g, BubieEntity b, float x, float y, float h, float yaw, float pitch) {
        float angle = 180F + yaw;
        b.yBodyRot = angle;
        b.yBodyRotO = angle;
        b.setYRot(angle);
        b.setXRot(0);
        b.yHeadRot = angle;
        b.yHeadRotO = angle;
        Quaternionf pose = new Quaternionf().rotateZ(Mth.PI).rotateX(-pitch * Mth.DEG_TO_RAD);
        Quaternionf camera = new Quaternionf().rotateX(-pitch * Mth.DEG_TO_RAD);
        InventoryScreen.renderEntityInInventory(g, x, y, h, new Vector3f(0, 0, 0), pose, camera, b);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
