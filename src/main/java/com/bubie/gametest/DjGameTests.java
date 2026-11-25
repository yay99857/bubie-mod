package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Dj;
import com.bubie.entity.Face;
import com.bubie.entity.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** The DJ on the server: the disc in its slot, and what clients are told to play. */
public class DjGameTests {
    private static BubieEntity dj(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(8, 1, 8));
        bubie.setEnergy(40000);
        return bubie;
    }

    /** Near a DJ, only the Bubies sitting in Stay mode dance; the ones following (or working) keep their own face. */
    @BubieTest(template = BubieGameTests.FIELD, timeoutTicks = 100)
    public static void onlyBubiesThatStayDanceAlong(GameTestHelper helper) {
        BubieEntity dj = dj(helper);
        dj.disc().setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
        BubieEntity sitting = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(6, 1, 8));
        BubieEntity following = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(10, 1, 8));
        sitting.setEnergy(40000);
        following.setEnergy(40000);
        sitting.setMode(Mode.STAY);
        following.setMode(Mode.FOLLOW);
        dj.dj().toggle();
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(dj.getFace() == Face.MUSIC, "the DJ shows the equalizer, has " + dj.getFace());
            helper.assertTrue(sitting.getFace() == Face.MUSIC, "the one sitting dances, has " + sitting.getFace());
            helper.assertTrue(following.getFace() != Face.MUSIC, "the one following does not dance");
            sitting.setMode(Mode.FOLLOW);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(sitting.getFace() != Face.MUSIC, "told to follow, it stops dancing at once");
                helper.succeed();
            });
        });
    }

    @BubieTest(template = BubieGameTests.FIELD, timeoutTicks = 100)
    public static void djPlaysTheDiscInItsSlot(GameTestHelper helper) {
        BubieEntity bubie = dj(helper);
        bubie.disc().setItem(0, new ItemStack(Items.MUSIC_DISC_CAT)); // the DJ tab's disc slot
        bubie.insert(new ItemStack(Items.MUSIC_DISC_PIGSTEP)); // a disc in storage is cargo, not music
        bubie.dj().toggle();
        Dj.Track first = bubie.getDjTrack();
        helper.assertTrue(!first.isNone() && first.ref().endsWith("music_disc.cat"), "plays the disc in its slot, plays " + first);
        // swapping the disc while it plays moves on to the new one
        bubie.disc().setItem(0, new ItemStack(Items.MUSIC_DISC_PIGSTEP));
        helper.runAfterDelay(8, () -> {
            Dj.Track second = bubie.getDjTrack();
            helper.assertTrue(second.ref().endsWith("music_disc.pigstep") && second.serial() > first.serial(),
                    "the new disc plays: " + second);
            helper.assertTrue(bubie.getFace() == Face.MUSIC, "dances with the equalizer face, has " + bubie.getFace());
            bubie.dj().toggle();
            helper.assertTrue(bubie.getDjTrack().isNone(), "stops");
            helper.succeed();
        });
    }

    /** Taking the disc out of its slot while it plays stops it; it doesn't play on. */
    @BubieTest(template = BubieGameTests.FIELD, timeoutTicks = 60)
    public static void djStopsWhenItsDiscIsTakenOut(GameTestHelper helper) {
        BubieEntity bubie = dj(helper);
        bubie.disc().setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
        bubie.dj().toggle();
        helper.assertTrue(bubie.getDjTrack().ref().endsWith("music_disc.cat"), "plays the disc");
        bubie.disc().setItem(0, ItemStack.EMPTY);
        helper.succeedWhen(() -> helper.assertTrue(bubie.getDjTrack().isNone() && !bubie.dj().isPlaying(),
                "stopped once the disc was taken, plays " + bubie.getDjTrack()));
    }

    @BubieTest(template = BubieGameTests.FIELD, timeoutTicks = 20)
    public static void djWithoutADiscStaysStopped(GameTestHelper helper) {
        BubieEntity bubie = dj(helper);
        bubie.insert(new ItemStack(Items.MUSIC_DISC_PIGSTEP)); // only in storage: cargo
        bubie.dj().toggle();
        helper.assertTrue(bubie.getDjTrack().isNone() && !bubie.dj().isPlaying(), "nothing to play: stays stopped");
        helper.succeed();
    }
}
