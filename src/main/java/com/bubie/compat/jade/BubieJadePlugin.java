package com.bubie.compat.jade;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Mode;
import com.bubie.entity.Status;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade: what a Bubie is doing, its job and its battery, without opening its menu. Everything is synced data. */
@WailaPlugin(Bubie.MOD_ID)
public class BubieJadePlugin implements IWailaPlugin {
    static final ResourceLocation INFO = Bubie.id("bubie");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(Info.INSTANCE, BubieEntity.class);
    }

    enum Info implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof BubieEntity bubie)) return;
            Mode mode = bubie.getMode();
            Component doing = mode == Mode.WORK
                    ? Component.translatable(bubie.getJob().key())
                    : Component.translatable(mode.key());
            tooltip.add(doing.copy().withStyle(ChatFormatting.GOLD));
            Status status = bubie.getStatus();
            tooltip.add(Component.translatable(status.key()).withStyle(status.problem ? ChatFormatting.RED : ChatFormatting.GRAY));
            tooltip.add(Component.translatable("bubie.tooltip.energy", Math.round(bubie.energyFraction() * 100))
                    .withStyle(ChatFormatting.GRAY));
            if (bubie.getOwnerUUID() != null) {
                Player owner = bubie.level().getPlayerByUUID(bubie.getOwnerUUID());
                if (owner != null) tooltip.add(owner.getName().copy().withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return INFO;
        }
    }
}
