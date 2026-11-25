package com.bubie.client;

import com.bubie.entity.Face;
import com.bubie.entity.Look;
import com.bubie.entity.Mode;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** What a frame of a Bubie needs, copied from the entity before it is drawn. */
public class BubieRenderState extends ArmedEntityRenderState {
    public Look look = Look.DEFAULT;
    public Face face = Face.IDLE;
    public Mode mode = Mode.FOLLOW;
    /** The glowing face texture for this frame (blinking and the music animation already picked). */
    @Nullable
    public Identifier faceTexture;
    /** The balloon over its head: the problem it has, or the song it is playing. */
    @Nullable
    public Component balloon;
    @Nullable
    public Vec3 balloonAttachment;
}
