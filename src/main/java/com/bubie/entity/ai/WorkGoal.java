package com.bubie.entity.ai;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.Mode;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** WORK mode: hands every tick to the job runner. */
public class WorkGoal extends Goal {
    private final BubieEntity bubie;

    public WorkGoal(BubieEntity bubie) {
        this.bubie = bubie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return bubie.getMode() == Mode.WORK;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        bubie.runner().tick();
    }

    @Override
    public void stop() {
        bubie.runner().pause();
    }
}
