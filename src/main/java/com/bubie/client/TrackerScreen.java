package com.bubie.client;

import com.bubie.item.WirelessStationItem;
import com.bubie.network.StationBubiesPayload;
import com.bubie.network.TrackerRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;

import java.util.List;

/**
 * The Wireless Station in the hand: the tracker of the Station it is linked to, asked from the server once a second.
 * It closes when the handheld leaves the hand.
 */
public class TrackerScreen extends Screen {
    private static final int W = 176, H = 214, PAD = 9, TOP = 30;

    private final InteractionHand hand;
    private int left, top, scroll, ticks;

    public TrackerScreen(InteractionHand hand) {
        super(Component.translatable("item.bubie.wireless_station"));
        this.hand = hand;
        StationBubiesPayload.latest = List.of();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        new TrackerRequestPayload().send();
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.player == null || WirelessStationItem.link(minecraft.player.getItemInHand(hand)) == null) {
            onClose();
            return;
        }
        if (++ticks % 20 == 0) new TrackerRequestPayload().send();
    }

    private int listHeight() {
        return H - TOP - PAD - 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        BubieUi.casing(g, left, top, W, H);
        g.drawString(font, title, left + PAD, top + 7, BubieUi.INK, false);
        WirelessStationItem.Link link = minecraft == null || minecraft.player == null ? null
                : WirelessStationItem.link(minecraft.player.getItemInHand(hand));
        if (link != null) {
            g.drawString(font, Component.translatable("bubie.ui.tracker.station", link.pos().getX(), link.pos().getY(),
                    link.pos().getZ()), left + PAD, top + 17, BubieUi.INK_SOFT, false);
        }
        BubieUi.screen(g, left + PAD - 3, top + TOP - 3, W - PAD * 2 + 6, listHeight() + 6);
        List<StationBubiesPayload.Entry> list = StationBubiesPayload.latest;
        scroll = Mth.clamp(scroll, 0, TrackerList.maxScroll(list, listHeight()));
        TrackerList.draw(g, font, left + PAD, top + TOP, W - PAD * 2, listHeight(), list, scroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int) (delta * TrackerList.ROW / 2), 0,
                TrackerList.maxScroll(StationBubiesPayload.latest, listHeight()));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
