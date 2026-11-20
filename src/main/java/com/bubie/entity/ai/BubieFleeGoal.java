package com.bubie.entity.ai;

import com.bubie.entity.BubieEntity;
import com.bubie.entity.Mode;
import com.bubie.entity.Status;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Monster;

/** Bubies don't fight: they run from monsters and go back to what they were doing. */
public class BubieFleeGoal extends AvoidEntityGoal<Monster> {
    private final BubieEntity bubie;

    public BubieFleeGoal(BubieEntity bubie) {
        super(bubie, Monster.class, 6.0F, 1.1, 1.4);
        this.bubie = bubie;
    }

    @Override
    public boolean canUse() {
        return bubie.getMode() != Mode.STAY && super.canUse();
    }

    @Override
    public void start() {
        super.start();
        bubie.runner().pause();
        bubie.setStatus(Status.FLEEING);
    }

    @Override
    public void stop() {
        super.stop();
        bubie.setStatus(Status.IDLE);
    }
}
