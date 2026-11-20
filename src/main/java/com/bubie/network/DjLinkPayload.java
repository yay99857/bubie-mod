package com.bubie.network;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: an audio link for the Bubie DJ's playlist. */
public record DjLinkPayload(int entityId, String url) implements CustomPacketPayload {
    public static final Type<DjLinkPayload> TYPE = new Type<>(Bubie.id("dj_link"));
    public static final StreamCodec<ByteBuf, DjLinkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DjLinkPayload::entityId,
            ByteBufCodecs.stringUtf8(1024), DjLinkPayload::url,
            DjLinkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DjLinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.level().getEntity(payload.entityId()) instanceof BubieEntity bubie)) return;
            if (!bubie.canCommand(player) || bubie.distanceToSqr(player) > 12 * 12) return;
            Component refused = bubie.dj().addLink(payload.url());
            player.displayClientMessage(refused != null ? refused.copy().withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("bubie.dj.added").withStyle(ChatFormatting.GREEN), true);
        });
    }
}
