package com.bubie.entity.ai;

import com.bubie.Bubie;
import com.bubie.BubieConfig;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Mode;
import com.bubie.entity.Status;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * A working Bubie with a station goes home when its battery is low or, if the server allows it, at night, and
 * stays there until it is charged (and it is day again). The station does the charging.
 */
public class RestGoal extends Goal {
    private final BubieEntity bubie;
    private BlockPos home;
    private int ticks, lastEnergy, sameEnergyTicks;

    public RestGoal(BubieEntity bubie) {
        this.bubie = bubie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean lowBattery(float below) {
        return BubieConfig.REQUIRE_ENERGY.get() && bubie.energyFraction() < below;
    }

    private boolean night() {
        return BubieConfig.REST_AT_NIGHT.get() && bubie.level().isNight();
    }

    private boolean homeExists() {
        BlockPos h = bubie.getHome();
        return h != null && bubie.level().isLoaded(h) && bubie.level().getBlockState(h).is(Bubie.STATION.get());
    }

    @Override
    public boolean canUse() {
        return bubie.getMode() == Mode.WORK && homeExists() && (lowBattery(0.1F) || night());
    }

    @Override
    public boolean canContinueToUse() {
        return bubie.getMode() == Mode.WORK && homeExists() && (lowBattery(0.95F) || night());
    }

    @Override
    public void start() {
        home = bubie.getHome();
        ticks = 0;
        sameEnergyTicks = 0;
        lastEnergy = bubie.getEnergy();
        bubie.runner().pause();
    }

    @Override
    public void stop() {
        bubie.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        double dist = bubie.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        if (dist > 2.6 * 2.6) {
            bubie.setStatus(Status.GOING_HOME);
            if (ticks % 20 == 1) bubie.getNavigation().moveTo(bubie.getNavigation().createPath(home, 1), 1.0);
            if (ticks > 400 && BubieConfig.TELEPORT_WHEN_STUCK.get()) {
                bubie.teleportNear(home);
                ticks = 0;
            }
            return;
        }
        bubie.getNavigation().stop();
        bubie.getLookControl().setLookAt(home.getX() + 0.5, home.getY() + 0.5, home.getZ() + 0.5);
        int energy = bubie.getEnergy();
        if (energy != lastEnergy) {
            sameEnergyTicks = 0;
            lastEnergy = energy;
        } else {
            sameEnergyTicks++;
        }
        if (lowBattery(0.95F)) {
            // A station without power: say so instead of sleeping forever.
            bubie.setStatus(energy == 0 && sameEnergyTicks > 100 ? Status.NO_ENERGY : Status.CHARGING);
        } else {
            bubie.setStatus(Status.RESTING);
        }
    }
}
