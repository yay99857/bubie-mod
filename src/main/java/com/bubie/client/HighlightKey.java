package com.bubie.client;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** While the "show my Bubies" key is held, the local player's Bubies glow through walls (see BubieEntity#isCurrentlyGlowing). */
@Mod.EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class HighlightKey {
    private HighlightKey() {
    }

    @SubscribeEvent
    static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean held = mc.player != null && mc.screen == null && ClientSetup.HIGHLIGHT.isDown();
        BubieEntity.highlightFor = held ? mc.player.getUUID() : null;
    }
}
