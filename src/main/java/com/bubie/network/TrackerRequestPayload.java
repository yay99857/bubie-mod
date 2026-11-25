package com.bubie.network;

import com.bubie.Bubie;
import com.bubie.item.WirelessStationItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server, once a second while the Wireless Station's screen is open: "what is my Station tracking?". The
 * answer is a {@link StationBubiesPayload} for the Station the handheld in the player's hand is linked to.
 */
public record TrackerRequestPayload() implements CustomPacketPayload {
    public static final Type<TrackerRequestPayload> TYPE = new Type<>(Bubie.id("tracker_request"));
    public static final StreamCodec<ByteBuf, TrackerRequestPayload> STREAM_CODEC = StreamCodec.unit(new TrackerRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrackerRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            WirelessStationItem.Link link = WirelessStationItem.held(player);
            if (link == null) return;
            PacketDistributor.sendToPlayer(player, StationBubiesPayload.of(player.level().getServer(), link.dimension(), link.pos(),
                    player.level().dimension()));
        });
    }
}
