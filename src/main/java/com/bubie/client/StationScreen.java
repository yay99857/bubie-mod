package com.bubie.client;

import com.bubie.menu.StationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.text.NumberFormat;

public class StationScreen extends AbstractContainerScreen<StationMenu> {
    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
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
        BubieUi.casing(g, x, y, imageWidth, imageHeight);
        for (int i = 0; i < 27; i++) BubieUi.slot(g, x + 8 + (i % 9) * 18, y + 18 + (i / 9) * 18);
        BubieUi.slot(g, x + StationMenu.FUEL_X, y + StationMenu.FUEL_Y);
        for (int i = 0; i < 27; i++) BubieUi.slot(g, x + 8 + (i % 9) * 18, y + StationMenu.INV_Y + (i / 9) * 18);
        for (int i = 0; i < 9; i++) BubieUi.slot(g, x + 8 + i * 18, y + StationMenu.INV_Y + 58);
        BubieUi.screen(g, x + 30, y + 88, 138, 22);
        BubieUi.bar(g, x + 34, y + 102, 130, 4, (float) menu.energy() / menu.capacity(), BubieUi.AMBER);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, BubieUi.INK, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, BubieUi.INK_SOFT, false);
        NumberFormat nf = NumberFormat.getIntegerInstance();
        String fe = nf.format(menu.energy()) + " / " + nf.format(menu.capacity()) + " FE";
        g.drawString(font, fe, 34, 91, BubieUi.AMBER, false);
        if (menu.charging()) {
            Component c = Component.translatable("bubie.ui.charging");
            g.drawString(font, c, 164 - font.width(c), 91, BubieUi.AMBER_LIGHT, false);
        }
    }
}
