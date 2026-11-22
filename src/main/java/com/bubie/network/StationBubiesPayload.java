package com.bubie.network;

import com.bubie.Bubie;
import com.bubie.data.BubieRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: the Bubies that live at a Station, for its tracker. Sent while the Station's menu is open, and
 * as the answer to a Wireless Station ({@link TrackerRequestPayload}).
 */
public record StationBubiesPayload(List<Entry> bubies) implements CustomPacketPayload {
    private static final int MAX_NAME = 64;

    /**
     * One Bubie: its name, job, status and face (ordinals), energy in percent, packed look, where it was last seen
     * and whether that is in another dimension than the player's.
     */
    public record Entry(String name, int job, int status, int face, int energy, int look, long pos, boolean elsewhere) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.of((buf, e) -> {
            Utf8String.write(buf, e.name, MAX_NAME);
            VarInt.write(buf, e.job + 16); // modes are sent as negative "jobs"
            VarInt.write(buf, e.status);
            VarInt.write(buf, e.face);
            VarInt.write(buf, e.energy);
            buf.writeInt(e.look);
            buf.writeLong(e.pos);
            buf.writeBoolean(e.elsewhere);
        }, buf -> new Entry(Utf8String.read(buf, MAX_NAME), VarInt.read(buf) - 16, VarInt.read(buf), VarInt.read(buf),
                VarInt.read(buf), buf.readInt(), buf.readLong(), buf.readBoolean()));

        public BlockPos blockPos() {
            return BlockPos.of(pos);
        }
    }

    public static final int MAX = 32;
    public static final Type<StationBubiesPayload> TYPE = new Type<>(Bubie.id("station_bubies"));
    public static final StreamCodec<ByteBuf, StationBubiesPayload> STREAM_CODEC = StreamCodec.composite(
            Entry.CODEC.apply(ByteBufCodecs.list(MAX)), StationBubiesPayload::bubies,
            StationBubiesPayload::new);

    /** What the open tracker shows; replaced by every update. */
    public static volatile List<Entry> latest = List.of();

    /** The Bubies living at a Station as the world's registry last saw them, for a player in the `viewer` dimension. */
    public static StationBubiesPayload of(MinecraftServer server, ResourceKey<Level> dimension, BlockPos station,
                                          ResourceKey<Level> viewer) {
        List<Entry> list = new ArrayList<>();
        String here = viewer.location().toString();
        for (BubieRegistry.Sighting s : BubieRegistry.get(server).livingAt(dimension.location().toString(), station)) {
            if (list.size() >= MAX) break;
            String name = s.name().length() > MAX_NAME ? s.name().substring(0, MAX_NAME) : s.name();
            list.add(new Entry(name, s.doing(), s.status(), s.face(), s.energy(), s.look(), s.pos(), !s.dimension().equals(here)));
        }
        return new StationBubiesPayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StationBubiesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> latest = payload.bubies());
    }
}
