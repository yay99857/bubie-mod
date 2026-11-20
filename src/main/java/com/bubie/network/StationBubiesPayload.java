package com.bubie.network;

import com.bubie.Bubie;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server to client, while a Station's menu is open: the Bubies that live there, for the list beside the slots. */
public record StationBubiesPayload(List<Entry> bubies) implements CustomPacketPayload {
    /** One Bubie: its name, job, status and face (ordinals), energy in percent and packed look. */
    public record Entry(String name, int job, int status, int face, int energy, int look) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Entry::name,
                ByteBufCodecs.VAR_INT, Entry::job,
                ByteBufCodecs.VAR_INT, Entry::status,
                ByteBufCodecs.VAR_INT, Entry::face,
                ByteBufCodecs.VAR_INT, Entry::energy,
                ByteBufCodecs.INT, Entry::look,
                Entry::new);
    }

    public static final int MAX = 8;
    public static final Type<StationBubiesPayload> TYPE = new Type<>(Bubie.id("station_bubies"));
    public static final StreamCodec<ByteBuf, StationBubiesPayload> STREAM_CODEC = StreamCodec.composite(
            Entry.CODEC.apply(ByteBufCodecs.list(MAX)), StationBubiesPayload::bubies,
            StationBubiesPayload::new);

    /** What the open Station screen shows; replaced by every update. */
    public static volatile List<Entry> latest = List.of();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StationBubiesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> latest = payload.bubies());
    }
}
