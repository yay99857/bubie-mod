package com.bubie.client;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.Dj;
import com.bubie.network.DjLinkPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** "Add link" from the DJ tab: paste an .mp3 or .ogg address for the Bubie to play. */
public class DjLinkScreen extends Screen {
    private static final int W = 300, H = 114;

    private final Screen parent;
    private final BubieEntity bubie;
    private EditBox field;
    private Button add;
    private int left, top;
    /** Why the text can't be added, shown under the field; null when it can (or the field is empty). */
    private Component problem;

    /** C:\..., C:/..., \\server\share, /home/..., file:... */
    static boolean isLocalFile(String text) {
        if (text.length() >= 3 && Character.isLetter(text.charAt(0)) && text.charAt(1) == ':'
                && (text.charAt(2) == '\\' || text.charAt(2) == '/')) return true;
        return text.startsWith("\\") || text.startsWith("/") || text.regionMatches(true, 0, "file:", 0, 5);
    }

    private static Component problem(String text) {
        if (text.isEmpty()) return null;
        // a file on this computer: the server and the others can't reach it
        if (isLocalFile(text)) return Component.translatable("bubie.ui.dj.local_file");
        if (!Dj.isValidLink(text)) return Component.translatable("bubie.dj.bad_link");
        String site = Dj.unsupportedSite(text);
        return site == null ? null : Component.translatable("bubie.dj.unsupported_site", site);
    }

    public DjLinkScreen(Screen parent, BubieEntity bubie) {
        super(Component.translatable("bubie.ui.dj.add_title"));
        this.parent = parent;
        this.bubie = bubie;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        field = new EditBox(font, left + 10, top + 34, W - 20, 18, Component.translatable("bubie.ui.dj.add_title"));
        field.setMaxLength(Dj.MAX_LINK_LENGTH);
        field.setHint(Component.literal("https://.../song.mp3"));
        field.setResponder(text -> {
            problem = problem(text.trim());
            add.active = problem == null && !text.isBlank();
        });
        addRenderableWidget(field);
        add = addRenderableWidget(Button.builder(Component.translatable("bubie.ui.dj.add_ok"), b -> submit())
                .bounds(left + W / 2 - 104, top + H - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + W / 2 + 4, top + H - 28, 100, 20).build());
        add.active = false;
        setInitialFocus(field);
    }

    /** For the screenshot director: types into the field. */
    void type(String text) {
        field.setValue(text);
    }

    private void submit() {
        String url = field.getValue().trim();
        if (problem(url) != null || !Dj.isValidLink(url)) return;
        PacketDistributor.sendToServer(new DjLinkPayload(bubie.getId(), url));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && add.active) {
            submit();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        BubieUi.casing(g, left, top, W, H);
        g.drawString(font, title, left + 10, top + 8, BubieUi.INK, false);
        g.drawString(font, Component.translatable("bubie.ui.dj.add_help"), left + 10, top + 20, BubieUi.INK_SOFT, false);
        if (problem != null) {
            int y = top + 55;
            for (var line : font.split(problem, W - 20)) {
                g.drawString(font, line, left + 10, y, 0xFFB03A2E, false); // a darker red, readable on the cream casing
                y += 9;
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
