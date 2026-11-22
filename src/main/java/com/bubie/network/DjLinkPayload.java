package com.bubie.network;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: an audio link for the Bubie DJ's playlist. */
public record DjLinkPayload(int entityId, String url) {
    private static final int MAX_URL = 1024;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(url, MAX_URL);
    }

    public static DjLinkPayload read(FriendlyByteBuf buf) {
        return new DjLinkPayload(buf.readVarInt(), buf.readUtf(MAX_URL));
    }

    public void send() {
        Bubie.CHANNEL.sendToServer(this);
    }

    public static void handle(DjLinkPayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            Player player = context.getSender();
            if (player == null || !(player.level().getEntity(payload.entityId()) instanceof BubieEntity bubie)) return;
            if (!bubie.canCommand(player) || bubie.distanceToSqr(player) > 12 * 12) return;
            Component refused = bubie.dj().addLink(payload.url());
            player.displayClientMessage(refused != null ? refused.copy().withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("bubie.dj.added").withStyle(ChatFormatting.GREEN), true);
        });
    }
}
