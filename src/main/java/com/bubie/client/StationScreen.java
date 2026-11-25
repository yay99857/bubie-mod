package com.bubie.client;

import com.bubie.menu.StationMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;

public class StationScreen extends AbstractContainerScreen<StationMenu> {
    /** The tracker, to the right of the slots: where each Bubie living here is and what it is doing. */
    private static final int LIST_X = 178, LIST_W = 150;
    private int scroll;

    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, LIST_X + LIST_W, 204);
        com.bubie.network.StationBubiesPayload.latest = java.util.List.of();
        inventoryLabelY = StationMenu.INV_Y - 11;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && hoveredSlot.index == 27 && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            g.setTooltipForNextFrame(font, font.split(Component.translatable("bubie.ui.hint.station_fuel"), 160), mouseX, mouseY);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractBackground(g, mx, my, partialTick);
        int x = leftPos, y = topPos;
        BubieUi.casing(g, x, y, 176, imageHeight);
        BubieUi.casing(g, x + LIST_X, y, LIST_W, imageHeight);
        BubieUi.screen(g, x + LIST_X + 5, y + 18, LIST_W - 10, imageHeight - 24);
        scroll = net.minecraft.util.Mth.clamp(scroll, 0, TrackerList.maxScroll(tracked(), listHeight()));
        TrackerList.draw(g, font, x + LIST_X + 9, y + 21, LIST_W - 18, listHeight(), tracked(), scroll);
        for (int i = 0; i < 27; i++) BubieUi.slot(g, x + 8 + (i % 9) * 18, y + 18 + (i / 9) * 18);
        BubieUi.slot(g, x + StationMenu.FUEL_X, y + StationMenu.FUEL_Y);
        for (int i = 0; i < 27; i++) BubieUi.slot(g, x + 8 + (i % 9) * 18, y + StationMenu.INV_Y + (i / 9) * 18);
        for (int i = 0; i < 9; i++) BubieUi.slot(g, x + 8 + i * 18, y + StationMenu.INV_Y + 58);
        BubieUi.screen(g, x + 30, y + 88, 138, 22);
        BubieUi.bar(g, x + 34, y + 102, 130, 4, (float) menu.energy() / menu.capacity(), BubieUi.AMBER);
    }

    private int listHeight() {
        return imageHeight - 30;
    }

    private java.util.List<com.bubie.network.StationBubiesPayload.Entry> tracked() {
        return com.bubie.network.StationBubiesPayload.latest;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + LIST_X) {
            scroll = net.minecraft.util.Mth.clamp(scroll - (int) (scrollY * TrackerList.ROW / 2), 0,
                    TrackerList.maxScroll(tracked(), listHeight()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, 8, 6, BubieUi.INK, false);
        g.text(font, playerInventoryTitle, 8, inventoryLabelY, BubieUi.INK_SOFT, false);
        NumberFormat nf = NumberFormat.getIntegerInstance();
        String fe = nf.format(menu.energy()) + " / " + nf.format(menu.capacity()) + " FE";
        g.text(font, fe, 34, 91, BubieUi.AMBER, false);
        g.text(font, Component.translatable("bubie.ui.tracker"), LIST_X + 6, 6, BubieUi.INK, false);
        if (menu.charging()) {
            Component c = Component.translatable("bubie.ui.charging");
            g.text(font, c, 164 - font.width(c), 91, BubieUi.AMBER_LIGHT, false);
        }
    }
}
