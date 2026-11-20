package com.bubie.client;

import com.bubie.entity.Face;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The look of the bubie.gg windows: a cream casing with a bevel, a dark "screen" inside and amber light. */
public final class BubieUi {
    public static final int OUTLINE = 0xFF3B3537;
    public static final int CREAM_HI = 0xFFFFF9EE, CREAM = 0xFFF3E7D5, CREAM_SHADE = 0xFFDDCBB2, CREAM_DEEP = 0xFFBFA586;
    public static final int SCREEN = 0xFF2A2629, SCREEN_DEEP = 0xFF1B181E, SCREEN_LINE = 0xFF3A3436;
    public static final int AMBER = 0xFFF4BF69, AMBER_LIGHT = 0xFFFFE6B0, AMBER_DARK = 0xFFC98F3A;
    public static final int INK = 0xFF3B3537, INK_SOFT = 0xFF605B54, DIM = 0xFF9E978C, RED = 0xFFE86A5A, GREEN = 0xFF8FD18A;

    private BubieUi() {
    }

    /** The cream casing: outline, light top-left edge, shaded bottom-right edge, stepped corners. */
    public static void casing(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y, x + w - 2, y + h, OUTLINE);
        g.fill(x, y + 2, x + w, y + h - 2, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, OUTLINE);
        g.fill(x + 2, y + 1, x + w - 2, y + h - 1, CREAM);
        g.fill(x + 1, y + 2, x + w - 1, y + h - 2, CREAM);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, CREAM_HI);
        g.fill(x + 2, y + 2, x + 3, y + h - 2, CREAM_HI);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, CREAM_SHADE);
        g.fill(x + w - 3, y + 2, x + w - 2, y + h - 2, CREAM_SHADE);
    }

    /** The recessed dark screen. */
    public static void screen(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, CREAM_DEEP);
        g.fill(x, y, x + w + 1, y + h + 1, CREAM_HI);
        g.fill(x, y, x + w, y + h, SCREEN);
        g.fill(x, y, x + w, y + 2, SCREEN_DEEP);
        g.fill(x, y, x + 2, y + h, SCREEN_DEEP);
    }

    /** A slot pocket on the casing. */
    public static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, CREAM_DEEP);
        g.fill(x, y, x + 17, y + 17, CREAM_HI);
        g.fill(x, y, x + 16, y + 16, 0xFFE8DEC9);
    }

    /** A locked slot (needs a Storage Chip). */
    public static void lockedSlot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, CREAM_SHADE);
        g.fill(x, y, x + 16, y + 16, 0xFFE2D6C2);
        for (int i = 0; i < 16; i += 4) g.fill(x + i, y + 15 - i, x + i + 2, y + 16 - i, CREAM_DEEP);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, OUTLINE);
        g.fill(x, y, x + w, y + h, SCREEN_DEEP);
        int filled = Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, color);
            g.fill(x, y, x + filled, y + 1, AMBER_LIGHT);
        }
    }

    /** A flat button on the dark screen; the active one lights up amber. */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component text, boolean active, boolean hover) {
        int bg = active ? AMBER : hover ? 0xFF4A4347 : SCREEN_LINE;
        g.fill(x, y, x + w, y + h, bg);
        if (!active) g.fill(x, y + h - 1, x + w, y + h, SCREEN_DEEP);
        else g.fill(x, y + h - 1, x + w, y + h, AMBER_DARK);
        int color = active ? SCREEN_DEEP : hover ? AMBER_LIGHT : AMBER;
        int tw = font.width(text);
        g.drawString(font, text, x + (w - tw) / 2, y + (h - 8) / 2, color, false);
    }

    /** The Bubie's screen with its face, at any size (scale = GUI pixels per site pixel). */
    public static void face(GuiGraphics g, com.bubie.entity.Look look, Face face, int x, int y, int scale) {
        int w = LookTextures.SCREEN_W * scale, h = LookTextures.SCREEN_H * scale;
        g.fill(x - scale, y - scale, x + w + scale, y + h + scale, SCREEN_DEEP);
        g.fill(x, y, x + w, y + h, SCREEN);
        g.blit(LookTextures.face(look, face, false), x, y, w, h, LookTextures.SCREEN_X, LookTextures.SCREEN_Y,
                LookTextures.SCREEN_W, LookTextures.SCREEN_H, LookTextures.FACE_SIZE, LookTextures.FACE_SIZE);
    }
}
