package com.bubie.network;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The name typed over the title of a Bubie's menu (empty: back to plain "Bubie"). */
public record RenamePayload(int entityId, String name) implements CustomPacketPayload {
    public static final Type<RenamePayload> TYPE = new Type<>(Bubie.id("rename"));
    public static final StreamCodec<ByteBuf, RenamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RenamePayload::entityId,
            ByteBufCodecs.stringUtf8(256), RenamePayload::name,
            RenamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RenamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.level().getEntity(payload.entityId()) instanceof BubieEntity bubie)) return;
            if (!bubie.canCommand(player) || bubie.distanceToSqr(player) > 12 * 12) return;
            bubie.rename(payload.name());
        });
    }
}
