package com.bubie.client;

import com.bubie.menu.StationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;

public class StationScreen extends AbstractContainerScreen<StationMenu> {
    /** The list of the station's Bubies, to the right of the slots. */
    private static final int LIST_X = 178, LIST_W = 128;

    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        com.bubie.network.StationBubiesPayload.latest = java.util.List.of();
        imageWidth = LIST_X + LIST_W;
        imageHeight = 204;
        inventoryLabelY = StationMenu.INV_Y - 11;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.index == 27 && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            g.renderTooltip(font, font.split(Component.translatable("bubie.ui.hint.station_fuel"), 160), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos, y = topPos;
        BubieUi.casing(g, x, y, 176, imageHeight);
        BubieUi.casing(g, x + LIST_X, y, LIST_W, imageHeight);
        BubieUi.screen(g, x + LIST_X + 5, y + 18, LIST_W - 10, imageHeight - 24);
        for (int i = 0; i < 27; i++) BubieUi.slot(g, x + 8 + (i % 9) * 18, y + 18 + (i / 9) * 18);
        BubieUi.slot(g, x + StationMenu.FUEL_X, y + StationMenu.FUEL_Y);
        for (int i = 0; i < 27; i++) BubieUi.slot(g, x + 8 + (i % 9) * 18, y + StationMenu.INV_Y + (i / 9) * 18);
        for (int i = 0; i < 9; i++) BubieUi.slot(g, x + 8 + i * 18, y + StationMenu.INV_Y + 58);
        BubieUi.screen(g, x + 30, y + 88, 138, 22);
        BubieUi.bar(g, x + 34, y + 102, 130, 4, (float) menu.energy() / menu.capacity(), BubieUi.AMBER);
    }

    private void drawBubies(GuiGraphics g) {
        g.drawString(font, Component.translatable("bubie.ui.station.bubies"), LIST_X + 6, 6, BubieUi.INK, false);
        java.util.List<com.bubie.network.StationBubiesPayload.Entry> list = com.bubie.network.StationBubiesPayload.latest;
        int x = LIST_X + 9, y = 22;
        if (list.isEmpty()) {
            for (net.minecraft.util.FormattedCharSequence line : font.split(Component.translatable("bubie.ui.station.none"), LIST_W - 18)) {
                g.drawString(font, line, x, y, BubieUi.DIM, false);
                y += 10;
            }
            return;
        }
        for (com.bubie.network.StationBubiesPayload.Entry e : list) {
            com.bubie.entity.Status status = com.bubie.entity.Status.byId(e.status());
            BubieUi.face(g, com.bubie.entity.Look.unpack(e.look()), com.bubie.entity.Face.byId(e.face()), x, y + 2, 1);
            int tx = x + LookTextures.SCREEN_W + 4, tw = LIST_W - 18 - LookTextures.SCREEN_W - 4;
            g.drawString(font, font.plainSubstrByWidth(e.name(), tw), tx, y, BubieUi.AMBER_LIGHT, false);
            Component doing = e.job() >= 0 ? Component.translatable(com.bubie.entity.JobType.byId(e.job()).key())
                    : Component.translatable(com.bubie.entity.Mode.byId(-1 - e.job()).key());
            g.drawString(font, font.plainSubstrByWidth(doing.getString(), tw), tx, y + 9, BubieUi.AMBER, false);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(status.key()).getString(), LIST_W - 18), x, y + 19,
                    status.problem ? BubieUi.RED : BubieUi.DIM, false);
            BubieUi.bar(g, x, y + 29, LIST_W - 18, 3, e.energy() / 100F, e.energy() < 10 ? BubieUi.RED : BubieUi.AMBER);
            y += 37;
            if (y > imageHeight - 40) break;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, BubieUi.INK, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, BubieUi.INK_SOFT, false);
        NumberFormat nf = NumberFormat.getIntegerInstance();
        String fe = nf.format(menu.energy()) + " / " + nf.format(menu.capacity()) + " FE";
        g.drawString(font, fe, 34, 91, BubieUi.AMBER, false);
        drawBubies(g);
        if (menu.charging()) {
            Component c = Component.translatable("bubie.ui.charging");
            g.drawString(font, c, 164 - font.width(c), 91, BubieUi.AMBER_LIGHT, false);
        }
    }
}
