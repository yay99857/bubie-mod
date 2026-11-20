package com.bubie.compat;

import com.baseblueprint.api.HelperBuildEvent;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.JobType;
import com.bubie.entity.Mode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * Base Blueprint 1.3.0+: "Build with Bubie" in the blueprint menu hands the blueprint to the player's nearest Bubie.
 * Only registered when that event class exists, so Bubie still runs with older Base Blueprint versions.
 */
public final class BlueprintHelper {
    static final double RANGE = 32;

    private BlueprintHelper() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BlueprintHelper::onHelperBuild);
    }

    private static void onHelperBuild(HelperBuildEvent event) {
        if (event.isAccepted()) return;
        ServerPlayer player = event.getPlayer();
        BubieEntity bubie = pick(player);
        if (bubie == null) return;
        event.accept(BlueprintCompat.handOver(bubie, player, event.getBlueprint(), event.getOrigin()));
    }

    /**
     * Which Bubie builds: one already on Builder duty first, then one that isn't busy (following or staying),
     * then any other; the nearest in each group.
     */
    @Nullable
    static BubieEntity pick(ServerPlayer player) {
        List<BubieEntity> mine = player.level().getEntitiesOfClass(BubieEntity.class,
                new AABB(player.blockPosition()).inflate(RANGE), b -> b.isAlive() && b.canCommand(player));
        return mine.stream().min(Comparator
                .comparingInt((BubieEntity b) -> b.getJob() == JobType.BUILDER ? 0 : b.getMode() != Mode.WORK ? 1 : 2)
                .thenComparingDouble(b -> b.distanceToSqr(player))).orElse(null);
    }
}
