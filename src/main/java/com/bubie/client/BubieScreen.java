package com.bubie.client;

import com.bubie.BubieConfig;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.DigFilter;
import com.bubie.entity.Dj;
import com.bubie.entity.JobType;
import com.bubie.entity.MineMode;
import com.bubie.entity.Mode;
import com.bubie.entity.Status;
import com.bubie.entity.WorkArea;
import com.bubie.item.ChipItem;
import com.bubie.item.ChipType;
import com.bubie.menu.BubieMenu;
import com.bubie.network.BubieCommandPayload;
import com.bubie.network.RenamePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Bubie's menu. Left: a dark "screen" with four tabs (Job, Area, Status, DJ). Right: the Bubie's storage, its tool,
 * fuel and chips, and the player's inventory, on the cream casing.
 */
public class BubieScreen extends AbstractContainerScreen<BubieMenu> {
    private enum Tab { JOB, AREA, STATUS, DJ }

    private static final int PANEL_X = 7, PANEL_Y = 7, PANEL_W = 108, PANEL_H = 214;
    private static Tab tab = Tab.JOB;

    /** For the screenshot director. */
    static void selectTab(int index) {
        tab = Tab.values()[index];
    }

    private record Hit(int x, int y, int w, int h, Runnable action, Component tip) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private int mouseX, mouseY;

    public BubieScreen(BubieMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = BubieMenu.GRID_X + 162 + 7;
        imageHeight = 228;
        inventoryLabelX = BubieMenu.GRID_X;
        inventoryLabelY = BubieMenu.INV_Y - 11;
    }

    private void send(int command, int value) {
        BubieEntity bubie = menu.bubie();
        if (bubie != null) PacketDistributor.sendToServer(new BubieCommandPayload(bubie.getId(), command, value));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        BubieMenu.djTabOpen = tab == Tab.DJ;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        // what a panel button does
        double rx = mouseX - leftPos, ry = mouseY - topPos;
        for (Hit hit : hits) {
            if (hit.tip() != null && hit.contains(rx, ry)) {
                g.renderTooltip(font, font.split(hit.tip(), 170), mouseX, mouseY);
                break;
            }
        }
        // what goes in an empty equipment slot
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            int index = hoveredSlot.index;
            String key = index == BubieMenu.TOOL ? "bubie.ui.hint.tool" : index == BubieMenu.FUEL ? "bubie.ui.hint.fuel"
                    : index == BubieMenu.DRIVE ? "bubie.ui.hint.drive"
                    : index >= BubieMenu.CHIPS && index < BubieMenu.DISC ? "bubie.ui.hint.chip"
                    : index == BubieMenu.DISC ? "bubie.ui.hint.disc" : null;
            if (key != null) g.renderTooltip(font, font.split(Component.translatable(key), 160), mouseX, mouseY);
        }
    }

    private int chips(ChipType type) {
        int n = 0;
        for (int i = BubieMenu.CHIPS; i < BubieMenu.DISC; i++) {
            if (menu.getSlot(i).getItem().getItem() instanceof ChipItem chip && chip.type() == type) n++;
        }
        return n;
    }

    private int maxAreaSide() {
        return BubieConfig.AREA_SIZE.get() * (1 + chips(ChipType.RANGE));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos, y = topPos;
        BubieUi.casing(g, x, y, imageWidth, imageHeight);
        BubieUi.screen(g, x + PANEL_X, y + PANEL_Y, PANEL_W, PANEL_H);
        int open = menu.openSlots();
        for (int i = 0; i < BubieEntity.STORAGE_SLOTS; i++) {
            int sx = x + BubieMenu.GRID_X + (i % 9) * 18, sy = y + BubieMenu.GRID_Y + (i / 9) * 18;
            if (i < open) BubieUi.slot(g, sx, sy);
            else BubieUi.lockedSlot(g, sx, sy);
        }
        if (tab == Tab.DJ) BubieUi.slot(g, x + BubieMenu.DISC_X, y + BubieMenu.DISC_Y);
        BubieUi.slot(g, x + BubieMenu.GRID_X, y + BubieMenu.EQUIP_Y);
        BubieUi.slot(g, x + BubieMenu.GRID_X + 24, y + BubieMenu.EQUIP_Y);
        BubieUi.slot(g, x + BubieMenu.GRID_X + BubieMenu.DRIVE_X, y + BubieMenu.EQUIP_Y);
        for (int i = 0; i < BubieEntity.CHIP_SLOTS; i++) BubieUi.slot(g, x + BubieMenu.GRID_X + 72 + i * 18, y + BubieMenu.EQUIP_Y);
        for (int i = 0; i < 27; i++) BubieUi.slot(g, x + BubieMenu.GRID_X + (i % 9) * 18, y + BubieMenu.INV_Y + (i / 9) * 18);
        for (int i = 0; i < 9; i++) BubieUi.slot(g, x + BubieMenu.GRID_X + i * 18, y + BubieMenu.INV_Y + 58);
        BubieEntity bubie = menu.bubie();
        // energy bar
        if (bubie != null) {
            int bx = x + BubieMenu.GRID_X + 44, by = y + 126;
            BubieUi.bar(g, bx, by, 90, 5, bubie.energyFraction(), bubie.energyFraction() < 0.1F ? BubieUi.RED : BubieUi.AMBER);
        }
    }

    // ------------------------------------------------------------- renaming (click the name over the storage)

    /** The name being typed; hidden unless renaming. */
    @Nullable
    private EditBox nameBox;

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(font, leftPos + BubieMenu.GRID_X - 2, topPos + 4, 140, 13, Component.translatable("bubie.ui.rename"));
        nameBox.setMaxLength(BubieEntity.MAX_NAME);
        nameBox.setVisible(false);
        addRenderableWidget(nameBox);
    }

    private boolean renaming() {
        return nameBox != null && nameBox.isVisible();
    }

    private void startRename() {
        BubieEntity bubie = menu.bubie();
        if (bubie == null || nameBox == null) return;
        nameBox.setValue(bubie.hasCustomName() ? bubie.getCustomName().getString() : "");
        nameBox.setVisible(true);
        setFocused(nameBox);
        nameBox.setFocused(true);
    }

    private void endRename(boolean save) {
        if (!renaming()) return;
        BubieEntity bubie = menu.bubie();
        if (save && bubie != null) PacketDistributor.sendToServer(new RenamePayload(bubie.getId(), nameBox.getValue()));
        nameBox.setFocused(false);
        nameBox.setVisible(false);
        setFocused(null);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (renaming()) {
            // typing a name: Enter keeps it, Esc drops it, and no key closes the menu (E is the inventory key)
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) endRename(true);
            else if (key == GLFW.GLFW_KEY_ESCAPE) endRename(false);
            else nameBox.keyPressed(key, scan, modifiers);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /** Its name now (it changes while the menu is open), with a pencil: click to rename. */
    private void drawName(GuiGraphics g, @Nullable BubieEntity bubie) {
        if (renaming()) return;
        Component name = bubie != null ? bubie.getDisplayName() : title;
        g.drawString(font, name, BubieMenu.GRID_X, 7, BubieUi.INK, false);
        if (bubie == null) return;
        int w = font.width(name);
        boolean hover = hover(BubieMenu.GRID_X - 1, 5, w + 13, 11);
        g.drawString(font, "✎", BubieMenu.GRID_X + w + 4, 7, hover ? BubieUi.AMBER_DARK : BubieUi.CREAM_DEEP, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        drawName(g, menu.bubie());
        g.drawString(font, Component.translatable("bubie.ui.tool"), BubieMenu.GRID_X, BubieMenu.EQUIP_Y - 10, BubieUi.INK_SOFT, false);
        g.drawString(font, Component.translatable("bubie.ui.chips"), BubieMenu.GRID_X + 72, BubieMenu.EQUIP_Y - 10, BubieUi.INK_SOFT, false);
        g.drawString(font, Component.translatable("bubie.ui.energy"), BubieMenu.GRID_X, 124, BubieUi.INK_SOFT, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, BubieUi.INK_SOFT, false);
        BubieEntity bubie = menu.bubie();
        if (bubie != null) {
            String pct = Math.round(bubie.energyFraction() * 100) + "%";
            g.drawString(font, pct, BubieMenu.GRID_X + 162 - font.width(pct), 124, BubieUi.INK_SOFT, false);
            int open = menu.openSlots();
            if (open < BubieEntity.STORAGE_SLOTS) {
                int rowY = BubieMenu.GRID_Y + (open / 9) * 18 + 4;
                Component lock = Component.translatable(bubie.drive().isEmpty() ? "bubie.ui.locked.no_drive" : "bubie.ui.locked");
                g.drawString(font, lock, BubieMenu.GRID_X + (162 - font.width(lock)) / 2, rowY, BubieUi.CREAM_DEEP, false);
            }
        }
        hits.clear();
        if (bubie == null) return;
        if (!renaming()) {
            hits.add(new Hit(BubieMenu.GRID_X - 1, 5, font.width(bubie.getDisplayName()) + 13, 11, this::startRename,
                    Component.translatable("bubie.ui.rename.tip")));
        }
        drawTabs(g);
        int cx = PANEL_X + 4, cy = PANEL_Y + 22;
        switch (tab) {
            case JOB -> drawJob(g, bubie, cx, cy);
            case AREA -> drawArea(g, bubie, cx, cy);
            case STATUS -> drawStatus(g, bubie, cx, cy);
            case DJ -> drawDj(g, bubie, cx, cy);
        }
    }

    // ------------------------------------------------------------- panel parts (coordinates relative to the GUI)

    private boolean hover(int x, int y, int w, int h) {
        int mx = mouseX - leftPos, my = mouseY - topPos;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component text, boolean active, Runnable action) {
        button(g, x, y, w, h, text, active, action, null);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component text, boolean active, Runnable action, Component tip) {
        BubieUi.button(g, font, x, y, w, h, text, active, hover(x, y, w, h));
        hits.add(new Hit(x, y, w, h, action, tip));
    }

    private static Component tip(String key, Object... args) {
        return Component.translatable("bubie.ui.tip." + key, args);
    }

    /** A tooltip over a spot of the panel that is not a button. */
    private void tipArea(int x, int y, int w, int h, Component tip) {
        hits.add(new Hit(x, y, w, h, null, tip));
    }

    /** Shows the work area in the world for ten seconds. */
    private static void preview(BubieEntity bubie) {
        bubie.previewUntil = bubie.level().getGameTime() + 200;
    }

    private void label(GuiGraphics g, Component text, int x, int y) {
        g.drawString(font, text, x, y, BubieUi.DIM, false);
    }

    private int paragraph(GuiGraphics g, Component text, int x, int y, int color) {
        for (FormattedCharSequence line : font.split(text, PANEL_W - 8)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    /** A how-to at the bottom of a tab, left out when it would run past the panel. */
    private void helpIfFits(GuiGraphics g, Component help, int x, int y) {
        if (y + font.split(help, PANEL_W - 8).size() * 10 <= PANEL_Y + PANEL_H - 2) paragraph(g, help, x, y, BubieUi.DIM);
    }

    private void drawTabs(GuiGraphics g) {
        // tabs as wide as their labels, sharing what is left
        Tab[] tabs = Tab.values();
        Component[] labels = new Component[tabs.length];
        int text = 0;
        for (int i = 0; i < tabs.length; i++) {
            labels[i] = Component.translatable("bubie.ui.tab." + tabs[i].name().toLowerCase());
            text += font.width(labels[i]);
        }
        int space = PANEL_W - 6 - (tabs.length - 1), extra = Math.max(0, space - text) / tabs.length;
        int x = PANEL_X + 3;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int w = font.width(labels[i]) + extra;
            if (i == tabs.length - 1) w = PANEL_X + 3 + space + (tabs.length - 1) - x;
            button(g, x, PANEL_Y + 4, w, 13, labels[i], tab == t, () -> tab = t);
            x += w + 1;
        }
    }

    private void drawJob(GuiGraphics g, BubieEntity bubie, int x, int y) {
        int w = PANEL_W - 8;
        label(g, Component.translatable("bubie.ui.mode"), x, y);
        y += 10;
        for (Mode mode : Mode.values()) {
            button(g, x, y, w, 13, Component.translatable(mode.key()), bubie.getMode() == mode,
                    () -> send(BubieCommandPayload.SET_MODE, mode.ordinal()), tip("mode." + mode.name().toLowerCase()));
            y += 15;
        }
        y += 4;
        label(g, Component.translatable("bubie.ui.job"), x, y);
        y += 10;
        JobType job = bubie.getJob();
        button(g, x, y, 14, 13, Component.literal("◀"), false, () -> send(BubieCommandPayload.SET_JOB, job.ordinal() - 1), tip("job"));
        button(g, x + w - 14, y, 14, 13, Component.literal("▶"), false, () -> send(BubieCommandPayload.SET_JOB, job.ordinal() + 1), tip("job"));
        BubieUi.button(g, font, x + 15, y, w - 30, 13, Component.translatable(job.key()), true, false);
        y += 17;
        y = paragraph(g, Component.translatable(job.descriptionKey()), x, y, BubieUi.AMBER_LIGHT);
        y += 4;
        if (job == JobType.DIGGER) {
            int half = (w - 2) / 2;
            DigFilter[] filters = DigFilter.values();
            for (int i = 0; i < filters.length; i++) {
                DigFilter f = filters[i];
                boolean on = (bubie.getDigFilter() & f.bit()) != 0;
                button(g, x + (i % 2) * (half + 2), y + (i / 2) * 15, half, 13,
                        Component.literal(on ? "✔ " : "  ").append(Component.translatable(f.key())), on,
                        () -> send(BubieCommandPayload.TOGGLE_FILTER, f.ordinal()));
            }
        } else if (job == JobType.MINER) {
            for (MineMode m : MineMode.values()) {
                button(g, x, y, w, 13, Component.translatable(m.key()), bubie.getMineMode() == m,
                        () -> send(BubieCommandPayload.SET_MINE_MODE, m.ordinal()));
                y += 15;
            }
        }
    }

    private static Component pos(BlockPos pos) {
        return Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    private void drawArea(GuiGraphics g, BubieEntity bubie, int x, int y) {
        int w = PANEL_W - 8;
        WorkArea marked = bubie.getMarkedArea();
        WorkArea work = bubie.getWorkArea();
        label(g, Component.translatable("bubie.ui.area"), x, y);
        y += 10;
        if (marked != null) {
            g.drawString(font, marked.describe(), x, y, BubieUi.AMBER, false);
            y += 10;
            g.drawString(font, Component.translatable("bubie.ui.area_max", maxAreaSide()), x, y, BubieUi.DIM, false);
            y += 14;
        } else if (bubie.getWorkCenter() != null) {
            // no marked area: it works around its chest or station, as far as the radius
            int r = bubie.getWorkRadius();
            g.drawString(font, Component.translatable(bubie.getChest() != null ? "bubie.ui.around_chest" : "bubie.ui.around_home"),
                    x, y, BubieUi.AMBER, false);
            y += 11;
            // changing it shows the new area in the world right away
            Component radiusTip = tip("radius", Math.max(2, Math.min(60, (maxAreaSide() - 1) / 2)));
            button(g, x, y, 13, 13, Component.literal("-"), false, () -> {
                send(BubieCommandPayload.SET_RADIUS, r - 1);
                preview(bubie);
            }, radiusTip);
            button(g, x + w - 13, y, 13, 13, Component.literal("+"), false, () -> {
                send(BubieCommandPayload.SET_RADIUS, r + 1);
                preview(bubie);
            }, radiusTip);
            tipArea(x + 14, y, w - 28, 13, radiusTip);
            Component radius = Component.translatable("bubie.ui.radius", r);
            g.drawString(font, radius, x + (w - font.width(radius)) / 2, y + 3, BubieUi.AMBER_LIGHT, false);
            y += 16;
            if (bubie.getJob() == JobType.QUARRY || bubie.getJob() == JobType.DIGGER) {
                y = paragraph(g, Component.translatable("bubie.ui.pit_in_front"), x, y, BubieUi.DIM);
                y += 2;
            }
        } else {
            g.drawString(font, Component.translatable("bubie.ui.none"), x, y, BubieUi.AMBER_DARK, false);
            y += 24;
        }
        label(g, Component.translatable("bubie.ui.chest"), x, y);
        y += 10;
        BlockPos chest = bubie.getChest();
        g.drawString(font, chest == null ? Component.translatable("bubie.ui.chest_none") : pos(chest), x, y,
                chest == null ? BubieUi.AMBER_DARK : BubieUi.AMBER, false);
        y += 14;
        label(g, Component.translatable("bubie.ui.home"), x, y);
        y += 10;
        BlockPos home = bubie.getHome();
        g.drawString(font, home == null ? Component.translatable("bubie.ui.none") : pos(home), x, y,
                home == null ? BubieUi.AMBER_DARK : BubieUi.AMBER, false);
        y += 16;
        button(g, x, y, w, 13, Component.translatable("bubie.ui.show"), false, () -> preview(bubie), tip("show"));
        y += 15;
        if (marked != null) {
            button(g, x, y, w, 13, Component.translatable("bubie.ui.clear_area"), false, () -> send(BubieCommandPayload.CLEAR_AREA, 0), tip("clear_area"));
            y += 15;
        }
        if (chest != null) {
            button(g, x, y, w, 13, Component.translatable("bubie.ui.clear_chest"), false, () -> send(BubieCommandPayload.CLEAR_CHEST, 0), tip("clear_chest"));
            y += 15;
        }
        if (home != null) {
            button(g, x, y, w, 13, Component.translatable("bubie.ui.clear_home"), false, () -> send(BubieCommandPayload.CLEAR_HOME, 0), tip("clear_home"));
            y += 15;
        }
        y += 4;
        // the how-to, when it fits under the buttons (with an area, a chest and a home set, the player knows it already)
        helpIfFits(g, Component.translatable("bubie.ui.area_help"), x, y);
    }

    private void drawStatus(GuiGraphics g, BubieEntity bubie, int x, int y) {
        int w = PANEL_W - 8;
        // the face, big
        int fw = LookTextures.SCREEN_W * 2, fh = LookTextures.SCREEN_H * 2;
        BubieUi.face(g, bubie.getLook(), bubie.getFace(), x + (w - fw) / 2, y + 2, 2);
        y += fh + 8;
        button(g, x, y, w, 13, Component.translatable("bubie.ui.edit_look"), false, () -> minecraft.setScreen(new LookScreen(this, bubie)));
        y += 19;
        Status status = bubie.getStatus();
        // with nothing to do, say what its job is looking for and where, instead of the plain "nothing to do"
        Component text = status == Status.NOTHING_TO_DO ? idleReason(bubie) : bubie.statusText();
        y = paragraph(g, text, x, y, status.problem ? BubieUi.RED : BubieUi.AMBER_LIGHT);
        if (status.problem) y = paragraph(g, Component.translatable(status.key() + ".help"), x, y, BubieUi.DIM);
        if (bubie.getJob() == JobType.BUILDER && bubie.getBuildTotal() > 0) {
            int total = bubie.getBuildTotal(), placed = total - bubie.getBuildLeft();
            y += 6;
            label(g, Component.translatable("bubie.ui.build", placed, total), x, y);
            y += 10;
            BubieUi.bar(g, x + 1, y, w - 2, 5, placed / (float) total, BubieUi.AMBER);
            y += 7;
        }
        y += 6;
        label(g, Component.translatable("bubie.ui.energy"), x, y);
        y += 10;
        BubieUi.bar(g, x + 1, y, w - 2, 5, bubie.energyFraction(), BubieUi.AMBER);
        y += 9;
        g.drawString(font, bubie.getEnergy() / 20 / 60 + " min", x, y, BubieUi.AMBER, false);
        y += 13;
        label(g, Component.translatable("bubie.ui.health"), x, y);
        y += 10;
        BubieUi.bar(g, x + 1, y, w - 2, 5, bubie.getHealth() / bubie.getMaxHealth(), BubieUi.GREEN);
        y += 11;
        label(g, Component.translatable("bubie.ui.chips"), x, y);
        y += 10;
        boolean any = false;
        for (ChipType chip : ChipType.values()) {
            int n = chips(chip);
            if (n == 0) continue;
            any = true;
            Component name = Component.translatable("item.bubie." + chip.id()).append(n > 1 ? " ×" + n : "");
            g.drawString(font, name, x, y, BubieUi.AMBER, false);
            tipArea(x, y - 1, font.width(name), 10, Component.translatable("item.bubie." + chip.id() + ".desc"));
            y += 10;
        }
        if (!any) g.drawString(font, Component.translatable("bubie.ui.none"), x, y, BubieUi.AMBER_DARK, false);
    }

    /** Why a Bubie with nothing to do is waiting: what its job looks for, and where. */
    private static Component idleReason(BubieEntity bubie) {
        Component where;
        if (bubie.getMarkedArea() != null) where = Component.translatable("bubie.status.where.area");
        else if (bubie.getChest() != null) where = Component.translatable("bubie.status.where.chest", bubie.getWorkRadius());
        else where = Component.translatable("bubie.status.where.home", bubie.getWorkRadius());
        return Component.translatable("bubie.status.nothing_to_do." + bubie.getJob().name().toLowerCase(), where);
    }

    private void drawDj(GuiGraphics g, BubieEntity bubie, int x, int y) {
        int w = PANEL_W - 8;
        Dj.Track track = bubie.getDjTrack();
        label(g, Component.translatable("bubie.ui.dj.now"), x, y);
        y += 10;
        // one line, so the disc slot below stays put
        if (track.isNone()) {
            g.drawString(font, Component.translatable("bubie.ui.dj.stopped"), x, y, BubieUi.AMBER_DARK, false);
        } else {
            String title = "♪ " + track.title();
            if (font.width(title) > w) title = font.plainSubstrByWidth(title, w - font.width("…")) + "…";
            g.drawString(font, title, x, y, BubieUi.AMBER_LIGHT, false);
        }
        y += 14;
        button(g, x, y, w, 13, Component.literal(track.isNone() ? "▶" : "■"), !track.isNone(),
                () -> send(BubieCommandPayload.DJ_TOGGLE, 0), tip(track.isNone() ? "dj.play" : "dj.stop"));
        y += 19;
        int volume = bubie.getDjVolume();
        label(g, Component.translatable("bubie.ui.dj.volume", volume), x, y);
        y += 10;
        button(g, x, y, 13, 11, Component.literal("-"), false, () -> send(BubieCommandPayload.DJ_VOLUME, Math.max(0, volume - 10)));
        button(g, x + w - 13, y, 13, 11, Component.literal("+"), false, () -> send(BubieCommandPayload.DJ_VOLUME, Math.min(100, volume + 10)));
        BubieUi.bar(g, x + 16, y + 3, w - 32, 5, volume / 100F, BubieUi.AMBER);
        // the disc slot itself is a menu slot at DISC_X, DISC_Y (its frame is drawn in renderBg)
        y = BubieMenu.DISC_Y;
        g.drawString(font, Component.translatable("bubie.ui.dj.disc"), BubieMenu.DISC_X + 22, y + 5, BubieUi.DIM, false);
        y += 22;
        helpIfFits(g, Component.translatable("bubie.ui.dj.help"), x, y);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // a click anywhere but on the name being typed keeps that name
        if (renaming() && !nameBox.isMouseOver(mx, my)) endRename(true);
        if (button == 0) {
            double rx = mx - leftPos, ry = my - topPos;
            for (Hit hit : List.copyOf(hits)) {
                if (hit.action() != null && hit.contains(rx, ry)) {
                    hit.action().run();
                    playClick();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
