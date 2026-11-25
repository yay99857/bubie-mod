package com.bubie.client;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.Look;
import com.bubie.network.BubieCommandPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * "Edit my Bubie": the My Bubie editor from bubie.gg, in the game. The Bubie on the left wears the look being tried
 * on (drag it to turn it); Save sends it to the server so every player sees it.
 */
public class LookScreen extends Screen {
    private static final int W = 324, H = 212, STAGE_X = 8, STAGE_Y = 20, STAGE_W = 112, STAGE_H = 160, RIGHT = 130;

    private record Hit(int x, int y, int w, int h, Runnable action, Component tip) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Screen parent;
    private final BubieEntity bubie;
    private final List<Hit> hits = new ArrayList<>();
    private Look look;
    private int left, top;
    private float yaw = -25.0F;
    private boolean dragging;
    private double lastX;
    private long spinPause;

    public LookScreen(Screen parent, BubieEntity bubie) {
        super(Component.translatable("bubie.look.title"));
        this.parent = parent;
        this.bubie = bubie;
        this.look = bubie.getSavedLook();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        bubie.previewLook = look;
    }

    private void set(Look next) {
        look = next;
        bubie.previewLook = next;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        bubie.previewLook = null;
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        if (minecraft.gui.screen() != this) bubie.previewLook = null;
    }

    private void save() {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new BubieCommandPayload(bubie.getId(), BubieCommandPayload.SET_LOOK, look.pack()));
        onClose();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        hits.clear();
        int mx = mouseX - left, my = mouseY - top;
        g.pose().pushMatrix();
        g.pose().translate(left, top);
        BubieUi.casing(g, 0, 0, W, H);
        g.text(font, title, 8, 7, BubieUi.INK, false);

        // stage
        BubieUi.screen(g, STAGE_X, STAGE_Y, STAGE_W, STAGE_H);
        for (int i = 0; i < 6; i++) {
            int y = STAGE_Y + STAGE_H - 34 + i * 6;
            g.fill(STAGE_X + 2, y, STAGE_X + STAGE_W, y + 1, 0x22F4BF69);
        }
        g.pose().popMatrix();
        if (!dragging && System.currentTimeMillis() > spinPause) yaw += partialTick * 0.8F;
        drawBubie(g, left + STAGE_X, top + STAGE_Y, STAGE_W, STAGE_H);
        g.pose().pushMatrix();
        g.pose().translate(left, top);
        Component hint = Component.translatable("bubie.look.drag");
        g.text(font, hint, STAGE_X + (STAGE_W - font.width(hint)) / 2, STAGE_Y + STAGE_H - 11, BubieUi.DIM, false);

        int y = 20;
        y = swatches(g, mx, my, y, "bubie.look.shell", Look.SHELLS, look.shell(), i -> set(look.withShell(i)));
        y = swatches(g, mx, my, y, "bubie.look.cloth", Look.CLOTHS, look.cloth(), i -> set(look.withCloth(i)));
        y = swatches(g, mx, my, y, "bubie.look.glow", Look.GLOWS, look.glow(), i -> set(look.withGlow(i)));
        y = picker(g, mx, my, y, "bubie.look.eyes", "bubie.look.eyes." + Look.EYES.get(look.eyes()),
                () -> set(look.withEyes(look.eyes() - 1)), () -> set(look.withEyes(look.eyes() + 1)));
        picker(g, mx, my, y, "bubie.look.mouth", "bubie.look.mouth." + Look.MOUTHS.get(look.mouth()),
                () -> set(look.withMouth(look.mouth() - 1)), () -> set(look.withMouth(look.mouth() + 1)));

        // bottom row
        int by = H - 24, bw = 44;
        button(g, mx, my, STAGE_X, by, 54, Component.translatable("bubie.look.random"), false,
                () -> set(Look.random(bubie.getRandom())));
        button(g, mx, my, STAGE_X + 58, by, 54, Component.translatable("bubie.look.default"), false, () -> set(Look.DEFAULT));
        button(g, mx, my, W - 8 - bw * 2 - 4, by, bw, Component.translatable("gui.cancel"), false, this::onClose);
        button(g, mx, my, W - 8 - bw, by, bw, Component.translatable("bubie.look.save"), true, this::save);
        g.pose().popMatrix();

        for (Hit hit : hits) {
            if (hit.tip() != null && hit.contains(mx, my)) g.setTooltipForNextFrame(font, hit.tip(), mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(g);
    }

    private void drawBubie(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        float scale = 92.0F;
        var renderer = minecraft.getEntityRenderDispatcher().getRenderer(bubie);
        var state = renderer.createRenderState(bubie, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
            living.bodyRot = 180.0F + yaw;
            living.yRot = 0.0F;
            living.xRot = 0.0F;
        }
        Quaternionf pose = new Quaternionf().rotateZ(Mth.PI).rotateX(-0.18F);
        Quaternionf camera = new Quaternionf().rotateX(-0.18F);
        // feet 30 px above the bottom of the stage (the picture's origin is its centre)
        Vector3f feet = new Vector3f(0, (h / 2.0F - 30.0F) / scale, 0);
        g.entity(state, scale, feet, pose, camera, x, y, x + w, y + h);
    }

    private int swatches(GuiGraphicsExtractor g, int mx, int my, int y, String label, List<Look.Swatch> list, int selected,
                         java.util.function.IntConsumer pick) {
        g.text(font, Component.translatable(label), RIGHT, y, BubieUi.INK_SOFT, false);
        y += 11;
        int size = 13, step = 15;
        for (int i = 0; i < list.size(); i++) {
            int sx = RIGHT + i * step;
            int[] ramp = list.get(i).ramp();
            boolean on = i == selected;
            boolean hover = mx >= sx && mx < sx + size && my >= y && my < y + size;
            if (on) g.fill(sx - 2, y - 2, sx + size + 2, y + size + 2, BubieUi.AMBER);
            g.fill(sx - 1, y - 1, sx + size + 1, y + size + 1, on ? BubieUi.OUTLINE : hover ? BubieUi.INK_SOFT : BubieUi.CREAM_DEEP);
            g.fill(sx, y, sx + size, y + size, 0xFF000000 | ramp[1]);
            g.fill(sx, y, sx + size, y + 2, 0xFF000000 | ramp[0]);
            g.fill(sx, y + size - 3, sx + size, y + size, 0xFF000000 | ramp[2]);
            final int index = i;
            hits.add(new Hit(sx, y, size, size, () -> pick.accept(index),
                    Component.translatable("bubie.color." + list.get(i).id())));
        }
        return y + size + 6;
    }

    private int picker(GuiGraphicsExtractor g, int mx, int my, int y, String label, String nameKey, Runnable prev, Runnable next) {
        g.text(font, Component.translatable(label), RIGHT, y, BubieUi.INK_SOFT, false);
        y += 11;
        int w = W - 8 - RIGHT;
        BubieUi.screen(g, RIGHT, y, w, 20);
        button(g, mx, my, RIGHT + 3, y + 3, 14, Component.literal("◀"), false, prev);
        button(g, mx, my, RIGHT + w - 17, y + 3, 14, Component.literal("▶"), false, next);
        BubieUi.face(g, look, Face.IDLE, RIGHT + 22, y + 3, 1);
        Component name = Component.translatable(nameKey);
        g.text(font, name, RIGHT + 56 + (w - 76 - font.width(name)) / 2, y + 6, BubieUi.AMBER, false);
        return y + 26;
    }

    private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, Component text, boolean active, Runnable action) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 14;
        if (active) {
            BubieUi.button(g, font, x, y, w, 14, text, true, hover);
        } else {
            g.fill(x, y, x + w, y + 14, hover ? BubieUi.CREAM_HI : BubieUi.CREAM_SHADE);
            g.fill(x, y + 13, x + w, y + 14, BubieUi.CREAM_DEEP);
            g.text(font, text, x + (w - font.width(text)) / 2, y + 3, BubieUi.INK, false);
        }
        hits.add(new Hit(x, y, w, 14, action, null));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        double mx = mouseX - left, my = mouseY - top;
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            for (Hit hit : List.copyOf(hits)) {
                if (hit.contains(mx, my)) {
                    hit.action().run();
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
            if (mx >= STAGE_X && mx < STAGE_X + STAGE_W && my >= STAGE_Y && my < STAGE_Y + STAGE_H) {
                dragging = true;
                lastX = mouseX;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        if (dragging) {
            yaw -= (float) (mouseX - lastX) * 2.0F;
            lastX = mouseX;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        if (dragging) {
            dragging = false;
            spinPause = System.currentTimeMillis() + 3000;
            return true;
        }
        return super.mouseReleased(event);
    }

    /** For the screenshot director. */
    void setLook(Look next) {
        set(next);
    }
}
