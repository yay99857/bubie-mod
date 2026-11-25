package com.bubie.client;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.WorkArea;
import com.bubie.item.ClipboardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import javax.annotation.Nullable;

/**
 * Outlines in the world: with a clipboard in hand, the selection (amber), its chest (teal) and home (cream), plus the
 * areas of your Bubies nearby; and for ten seconds after "Show in world" in a Bubie menu, that Bubie area.
 */
@EventBusSubscriber(modid = Bubie.MOD_ID, value = Dist.CLIENT)
public final class AreaPreview {
    private AreaPreview() {
    }

    @Nullable
    private static ItemStack clipboard(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof ClipboardItem) return stack;
        }
        return null;
    }

    /** Once a tick, as gizmos: the game keeps drawing them every frame until the next tick. */
    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        ItemStack clipboard = clipboard(player);
        long now = mc.level.getGameTime();

        if (clipboard != null) {
            ClipboardItem.Selection s = ClipboardItem.read(clipboard);
            if (s.dimension().equals(mc.level.dimension().identifier().toString())) {
                WorkArea area = s.area();
                if (area != null) box(area.box(), 0.96F, 0.75F, 0.41F, 1.0F);
                else if (s.a() != null) box(new AABB(s.a()), 0.96F, 0.75F, 0.41F, 1.0F);
                if (s.chest() != null) box(new AABB(s.chest()).inflate(0.02), 0.36F, 0.75F, 0.75F, 1.0F);
                if (s.home() != null) box(new AABB(s.home()).inflate(0.03), 1.0F, 0.98F, 0.93F, 1.0F);
            }
        }
        for (BubieEntity bubie : mc.level.getEntitiesOfClass(BubieEntity.class, player.getBoundingBox().inflate(64))) {
            boolean shown = bubie.previewUntil > now;
            if (!shown && (clipboard == null || !bubie.isOwnedBy(player))) continue;
            WorkArea area = bubie.getWorkArea();
            float a = shown ? 1.0F : 0.45F;
            if (area != null) box(area.box().inflate(0.01), 0.95F, 0.91F, 0.84F, a);
            BlockPos chest = bubie.getChest();
            if (shown && chest != null) box(new AABB(chest).inflate(0.02), 0.36F, 0.75F, 0.75F, 1.0F);
            BlockPos home = bubie.getHome();
            if (shown && home != null) box(new AABB(home).inflate(0.03), 1.0F, 0.98F, 0.93F, 1.0F);
        }
    }

    private static void box(AABB box, float r, float g, float b, float a) {
        Gizmos.cuboid(box, GizmoStyle.stroke(ARGB.colorFromFloat(a, r, g, b), 2.0F));
    }
}
