package com.bubie.client;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.Face;
import com.bubie.entity.Look;
import com.bubie.network.BubieCommandPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        if (minecraft.screen != this) bubie.previewLook = null;
    }

    private void save() {
        PacketDistributor.sendToServer(new BubieCommandPayload(bubie.getId(), BubieCommandPayload.SET_LOOK, look.pack()));
        onClose();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hits.clear();
        int mx = mouseX - left, my = mouseY - top;
        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        BubieUi.casing(g, 0, 0, W, H);
        g.drawString(font, title, 8, 7, BubieUi.INK, false);

        // stage
        BubieUi.screen(g, STAGE_X, STAGE_Y, STAGE_W, STAGE_H);
        for (int i = 0; i < 6; i++) {
            int y = STAGE_Y + STAGE_H - 34 + i * 6;
            g.fill(STAGE_X + 2, y, STAGE_X + STAGE_W, y + 1, 0x22F4BF69);
        }
        g.pose().popPose();
        if (!dragging && System.currentTimeMillis() > spinPause) yaw += partialTick * 0.8F;
        drawBubie(g, left + STAGE_X, top + STAGE_Y, STAGE_W, STAGE_H);
        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        Component hint = Component.translatable("bubie.look.drag");
        g.drawString(font, hint, STAGE_X + (STAGE_W - font.width(hint)) / 2, STAGE_Y + STAGE_H - 11, BubieUi.DIM, false);

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
        g.pose().popPose();

        for (Hit hit : hits) {
            if (hit.tip() != null && hit.contains(mx, my)) g.renderTooltip(font, hit.tip(), mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    private void drawBubie(GuiGraphics g, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
        float bodyRot = bubie.yBodyRot, yRot = bubie.getYRot(), xRot = bubie.getXRot();
        float headO = bubie.yHeadRotO, head = bubie.yHeadRot, bodyO = bubie.yBodyRotO;
        float angle = 180.0F + yaw;
        bubie.yBodyRot = angle;
        bubie.yBodyRotO = angle;
        bubie.setYRot(angle);
        bubie.setXRot(0);
        bubie.yHeadRot = angle;
        bubie.yHeadRotO = angle;
        float scale = 92.0F;
        Quaternionf pose = new Quaternionf().rotateZ(Mth.PI).rotateX(-0.18F);
        Quaternionf camera = new Quaternionf().rotateX(-0.18F);
        InventoryScreen.renderEntityInInventory(g, x + w / 2.0F, y + h - 30, scale,
                new Vector3f(0, 0, 0), pose, camera, bubie);
        bubie.yBodyRot = bodyRot;
        bubie.yBodyRotO = bodyO;
        bubie.setYRot(yRot);
        bubie.setXRot(xRot);
        bubie.yHeadRot = head;
        bubie.yHeadRotO = headO;
        g.disableScissor();
    }

    private int swatches(GuiGraphics g, int mx, int my, int y, String label, List<Look.Swatch> list, int selected,
                         java.util.function.IntConsumer pick) {
        g.drawString(font, Component.translatable(label), RIGHT, y, BubieUi.INK_SOFT, false);
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

    private int picker(GuiGraphics g, int mx, int my, int y, String label, String nameKey, Runnable prev, Runnable next) {
        g.drawString(font, Component.translatable(label), RIGHT, y, BubieUi.INK_SOFT, false);
        y += 11;
        int w = W - 8 - RIGHT;
        BubieUi.screen(g, RIGHT, y, w, 20);
        button(g, mx, my, RIGHT + 3, y + 3, 14, Component.literal("◀"), false, prev);
        button(g, mx, my, RIGHT + w - 17, y + 3, 14, Component.literal("▶"), false, next);
        BubieUi.face(g, look, Face.IDLE, RIGHT + 22, y + 3, 1);
        Component name = Component.translatable(nameKey);
        g.drawString(font, name, RIGHT + 56 + (w - 76 - font.width(name)) / 2, y + 6, BubieUi.AMBER, false);
        return y + 26;
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, Component text, boolean active, Runnable action) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 14;
        if (active) {
            BubieUi.button(g, font, x, y, w, 14, text, true, hover);
        } else {
            g.fill(x, y, x + w, y + 14, hover ? BubieUi.CREAM_HI : BubieUi.CREAM_SHADE);
            g.fill(x, y + 13, x + w, y + 14, BubieUi.CREAM_DEEP);
            g.drawString(font, text, x + (w - font.width(text)) / 2, y + 3, BubieUi.INK, false);
        }
        hits.add(new Hit(x, y, w, 14, action, null));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = mouseX - left, my = mouseY - top;
        if (button == 0) {
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            yaw -= (float) (mouseX - lastX) * 2.0F;
            lastX = mouseX;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            spinPause = System.currentTimeMillis() + 3000;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** For the screenshot director. */
    void setLook(Look next) {
        set(next);
    }
}
