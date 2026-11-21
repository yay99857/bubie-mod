package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Dj;
import com.bubie.entity.Face;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The DJ's playlist on the server: discs from the storage, then links, and what clients are told to play. */
@GameTestHolder(Bubie.MOD_ID)
@PrefixGameTestTemplate(false)
public class DjGameTests {
    private static BubieEntity dj(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(8, 1, 8));
        bubie.setEnergy(40000);
        return bubie;
    }

    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 100)
    public static void djPlaysDiscsThenLinksInOrder(GameTestHelper helper) {
        BubieEntity bubie = dj(helper);
        bubie.disc().setItem(0, new ItemStack(Items.MUSIC_DISC_CAT)); // the DJ tab's disc slot
        bubie.insert(new ItemStack(Items.COBBLESTONE, 10)); // not music
        bubie.insert(new ItemStack(Items.MUSIC_DISC_PIGSTEP)); // a disc in storage is cargo, not the playlist
        helper.assertTrue(bubie.dj().addLink("https://example.com/music/My_Song.mp3") == null, "a good link is accepted");

        bubie.dj().toggle();
        Dj.Track first = bubie.getDjTrack();
        helper.assertTrue(!first.isNone() && first.kind().equals("disc") && first.ref().endsWith("music_disc.cat"),
                "starts with the disc in its slot, plays " + first);
        bubie.dj().next();
        Dj.Track third = bubie.getDjTrack();
        helper.assertTrue(third.isLink() && third.title().equals("My Song") && third.serial() > first.serial(),
                "then the link, titled from its file (not the disc in storage): " + third);
        bubie.dj().next();
        helper.assertTrue(bubie.getDjTrack().ref().endsWith("music_disc.cat"), "and back to the start");
        bubie.dj().previous();
        helper.assertTrue(bubie.getDjTrack().isLink(), "previous goes back to the link");

        // a client reports the end of the link: only that exact track moves the playlist on
        int serial = bubie.getDjTrack().serial();
        bubie.dj().trackDone(serial - 1);
        helper.assertTrue(bubie.getDjTrack().serial() == serial, "an old report changes nothing");
        bubie.dj().trackDone(serial);
        helper.assertTrue(bubie.getDjTrack().ref().endsWith("music_disc.cat"), "the reported end plays the next");

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(bubie.getFace() == Face.MUSIC, "dances with the equalizer face, has " + bubie.getFace());
            bubie.dj().toggle();
            helper.assertTrue(bubie.getDjTrack().isNone(), "stops");
            helper.succeed();
        });
    }

    /** Taking the disc out of its slot while it plays stops it (or moves on to the links); it doesn't play on. */
    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 60)
    public static void djStopsWhenItsDiscIsTakenOut(GameTestHelper helper) {
        BubieEntity bubie = dj(helper);
        bubie.disc().setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
        bubie.dj().toggle();
        helper.assertTrue(bubie.getDjTrack().ref().endsWith("music_disc.cat"), "plays the disc");
        bubie.disc().setItem(0, ItemStack.EMPTY);
        helper.succeedWhen(() -> helper.assertTrue(bubie.getDjTrack().isNone() && !bubie.dj().isPlaying(),
                "stopped once the disc was taken, plays " + bubie.getDjTrack()));
    }

    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 20)
    public static void djRefusesBadLinksAndStopsWithoutMusic(GameTestHelper helper) {
        BubieEntity bubie = dj(helper);
        helper.assertTrue(bubie.dj().addLink("file:///etc/passwd") != null, "file links are refused");
        helper.assertTrue(bubie.dj().addLink("javascript:alert(1)") != null, "other schemes are refused");
        helper.assertTrue(bubie.dj().addLink("not a link") != null, "text is refused");
        helper.assertTrue(bubie.dj().addLink("https://example.com/a.ogg\nb") != null, "line breaks are refused");
        for (int i = 0; i < Dj.MAX_LINKS; i++) bubie.dj().addLink("https://example.com/" + i + ".ogg");
        helper.assertTrue(bubie.dj().addLink("https://example.com/extra.ogg") != null, "the playlist has a limit");
        helper.assertTrue(bubie.getDjLinks().size() == Dj.MAX_LINKS, "clients see the links");
        bubie.dj().removeLink(0);
        helper.assertTrue(bubie.getDjLinks().size() == Dj.MAX_LINKS - 1, "removing works");
        while (!bubie.getDjLinks().isEmpty()) bubie.dj().removeLink(0);

        bubie.dj().toggle();
        helper.assertTrue(bubie.getDjTrack().isNone() && !bubie.dj().isPlaying(), "nothing to play: stays stopped");
        helper.succeed();
    }
}
