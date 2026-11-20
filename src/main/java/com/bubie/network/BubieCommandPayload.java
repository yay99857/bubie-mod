package com.bubie.network;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.DigFilter;
import com.bubie.entity.JobType;
import com.bubie.entity.MineMode;
import com.bubie.entity.Mode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A button in the Bubie's menu, sent to the server. */
public record BubieCommandPayload(int entityId, int command, int value) implements CustomPacketPayload {
    public static final int SET_MODE = 0, SET_JOB = 1, TOGGLE_FILTER = 2, SET_MINE_MODE = 3,
            CLEAR_AREA = 4, CLEAR_CHEST = 5, CLEAR_HOME = 6, SET_LOOK = 7,
            DJ_TOGGLE = 8, DJ_NEXT = 9, DJ_PREVIOUS = 10, DJ_VOLUME = 11, DJ_REMOVE_LINK = 12, DJ_TRACK_DONE = 13, SET_RADIUS = 14;

    public static final Type<BubieCommandPayload> TYPE = new Type<>(Bubie.id("command"));
    public static final StreamCodec<ByteBuf, BubieCommandPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BubieCommandPayload::entityId,
            ByteBufCodecs.VAR_INT, BubieCommandPayload::command,
            ByteBufCodecs.VAR_INT, BubieCommandPayload::value,
            BubieCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BubieCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player.level().getEntity(payload.entityId()) instanceof BubieEntity bubie)) return;
            int v = payload.value();
            // anyone who heard a link end reports it (the owner may be away); it only skips that exact track
            if (payload.command() == DJ_TRACK_DONE) {
                if (bubie.distanceToSqr(player) < 96 * 96) bubie.dj().trackDone(v);
                return;
            }
            if (!bubie.canCommand(player) || bubie.distanceToSqr(player) > 12 * 12) return;
            switch (payload.command()) {
                case SET_MODE -> bubie.setMode(Mode.byId(v));
                case SET_JOB -> {
                    // without Base Blueprint the Builder is skipped, in whichever direction the player was going
                    JobType job = JobType.byId(v);
                    if (job == JobType.BUILDER && !com.bubie.compat.BlueprintCompat.loaded()) {
                        job = JobType.byId(v + (v > bubie.getJob().ordinal() ? 1 : -1));
                    }
                    bubie.setJob(job);
                }
                case TOGGLE_FILTER -> {
                    if (v >= 0 && v < DigFilter.values().length) bubie.setDigFilter(bubie.getDigFilter() ^ DigFilter.values()[v].bit());
                }
                case SET_MINE_MODE -> bubie.setMineMode(MineMode.byId(v));
                case CLEAR_AREA -> bubie.setArea(null);
                case CLEAR_CHEST -> bubie.setChest(null);
                case CLEAR_HOME -> bubie.setHome(null);
                case SET_LOOK -> {
                    bubie.setLook(com.bubie.entity.Look.unpack(v));
                    bubie.showMood(com.bubie.entity.Face.HAPPY, 60);
                    bubie.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.6F);
                }
                case SET_RADIUS -> bubie.setWorkRadius(v);
                case DJ_TOGGLE -> bubie.dj().toggle();
                case DJ_NEXT -> bubie.dj().next();
                case DJ_PREVIOUS -> bubie.dj().previous();
                case DJ_VOLUME -> bubie.setDjVolume(v);
                case DJ_REMOVE_LINK -> bubie.dj().removeLink(v);
                default -> {
                }
            }
        });
    }
}
