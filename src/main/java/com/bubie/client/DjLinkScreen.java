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
    private static final int W = 300, H = 104;

    private final Screen parent;
    private final BubieEntity bubie;
    private EditBox field;
    private Button add;
    private int left, top;

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
        field.setResponder(text -> add.active = Dj.isValidLink(text.trim()));
        addRenderableWidget(field);
        add = addRenderableWidget(Button.builder(Component.translatable("bubie.ui.dj.add_ok"), b -> submit())
                .bounds(left + W / 2 - 104, top + H - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + W / 2 + 4, top + H - 28, 100, 20).build());
        add.active = false;
        setInitialFocus(field);
    }

    private void submit() {
        String url = field.getValue().trim();
        if (!Dj.isValidLink(url)) return;
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
