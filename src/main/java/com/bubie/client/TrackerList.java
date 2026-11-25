package com.bubie.client;

import com.bubie.entity.Face;
import com.bubie.entity.JobType;
import com.bubie.entity.Look;
import com.bubie.entity.Mode;
import com.bubie.entity.Status;
import com.bubie.network.StationBubiesPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * The tracker: one row per Bubie of a Station with its face, what it is doing, where it is and how far, and its
 * battery. Shared by the Station's menu and the Wireless Station's screen. Coordinates are screen pixels.
 */
public final class TrackerList {
    public static final int ROW = 46;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private TrackerList() {
    }

    /** How far the list can be scrolled, in pixels. */
    public static int maxScroll(List<StationBubiesPayload.Entry> list, int h) {
        return Math.max(0, list.size() * ROW - h);
    }

    public static void draw(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, List<StationBubiesPayload.Entry> list, int scroll) {
        if (list.isEmpty()) {
            int ty = y;
            for (FormattedCharSequence line : font.split(Component.translatable("bubie.ui.station.none"), w)) {
                g.text(font, line, x, ty, BubieUi.DIM, false);
                ty += 10;
            }
            return;
        }
        Player player = Minecraft.getInstance().player;
        int max = maxScroll(list, h);
        boolean bar = max > 0;
        int rw = bar ? w - 5 : w;
        g.enableScissor(x, y, x + w, y + h);
        int ry = y - scroll;
        for (StationBubiesPayload.Entry e : list) {
            if (ry + ROW > y && ry < y + h) row(g, font, x, ry, rw, e, player);
            ry += ROW;
        }
        g.disableScissor();
        if (bar) {
            int total = list.size() * ROW, knob = Math.max(12, h * h / total), ky = y + (h - knob) * scroll / max;
            g.fill(x + w - 3, y, x + w - 1, y + h, BubieUi.SCREEN_DEEP);
            g.fill(x + w - 3, ky, x + w - 1, ky + knob, BubieUi.AMBER);
        }
    }

    private static void row(GuiGraphicsExtractor g, Font font, int x, int y, int w, StationBubiesPayload.Entry e, Player player) {
        Status status = Status.byId(e.status());
        BubieUi.face(g, Look.unpack(e.look()), Face.byId(e.face()), x, y + 2, 1);
        int tx = x + LookTextures.SCREEN_W + 4, tw = w - LookTextures.SCREEN_W - 4;

        // how far and which way, from where the player stands and looks
        Component far = Component.translatable("bubie.ui.tracker.elsewhere");
        BlockPos pos = e.blockPos();
        if (!e.elsewhere() && player != null) {
            double dx = pos.getX() + 0.5 - player.getX(), dz = pos.getZ() + 0.5 - player.getZ();
            double dy = pos.getY() - player.getY();
            int blocks = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
            float turn = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - player.getYRot());
            String arrow = blocks < 2 ? "•" : ARROWS[Math.floorMod(Math.round(turn / 45.0F), 8)];
            far = Component.literal(arrow + " ").append(Component.translatable("bubie.ui.tracker.distance", blocks));
        }
        int fw = e.elsewhere() ? 0 : font.width(far);
        g.text(font, font.plainSubstrByWidth(e.name(), tw - fw - 3), tx, y, BubieUi.AMBER_LIGHT, false);
        if (!e.elsewhere()) g.text(font, far, x + w - fw, y, BubieUi.AMBER, false);

        Component doing = e.job() >= 0 ? Component.translatable(JobType.byId(e.job()).key())
                : Component.translatable(Mode.byId(-1 - e.job()).key());
        g.text(font, font.plainSubstrByWidth(doing.getString(), tw), tx, y + 9, BubieUi.AMBER, false);
        g.text(font, font.plainSubstrByWidth(Component.translatable(status.key()).getString(), w), x, y + 19,
                status.problem ? BubieUi.RED : BubieUi.DIM, false);
        String where = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        g.text(font, e.elsewhere() ? where + " (" + far.getString() + ")" : where, x, y + 29, BubieUi.DIM, false);
        BubieUi.bar(g, x, y + 40, w, 2, e.energy() / 100F, e.energy() < 10 ? BubieUi.RED : BubieUi.AMBER);
    }
}
