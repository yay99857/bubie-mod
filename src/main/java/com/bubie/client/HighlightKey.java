package com.bubie.client;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** While the "show my Bubies" key is held, the local player's Bubies glow through walls (see BubieEntity#isCurrentlyGlowing). */
@EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class HighlightKey {
    private HighlightKey() {
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean held = mc.player != null && mc.gui.screen() == null && ClientSetup.HIGHLIGHT.isDown();
        BubieEntity.highlightFor = held ? mc.player.getUUID() : null;
    }
}
