package com.bubie.network;

import com.bubie.Bubie;
import com.bubie.item.WirelessStationItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server, once a second while the Wireless Station's screen is open: "what is my Station tracking?". The
 * answer is a {@link StationBubiesPayload} for the Station the handheld in the player's hand is linked to.
 */
public record TrackerRequestPayload() {
    public void write(FriendlyByteBuf buf) {
    }

    public static TrackerRequestPayload read(FriendlyByteBuf buf) {
        return new TrackerRequestPayload();
    }

    public void send() {
        Bubie.CHANNEL.sendToServer(this);
    }

    public static void handle(TrackerRequestPayload payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.setPacketHandled(true);
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            WirelessStationItem.Link link = WirelessStationItem.held(player);
            if (link == null) return;
            StationBubiesPayload.of(player.server, link.dimension(), link.pos(),
                    player.level().dimension()).sendTo(player);
        });
    }
}
