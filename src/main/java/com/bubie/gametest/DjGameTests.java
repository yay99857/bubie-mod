package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.client.dj.LinkResolver;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Dj;
import com.bubie.entity.Face;
import com.bubie.entity.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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

    /** Near a DJ, only the Bubies sitting in Stay mode dance; the ones following (or working) keep their own face. */
    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 100)
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

    @GameTest(template = BubieGameTests.FIELD, timeoutTicks = 20)
    public static void djTakesShareLinksAndRefusesStreamingServices(GameTestHelper helper) {
        BubieEntity bubie = dj(helper);
        for (String url : new String[]{"https://www.youtube.com/watch?v=abc", "https://youtu.be/abc", "https://music.youtube.com/watch?v=abc",
                "https://soundcloud.com/artist/song", "https://open.spotify.com/track/abc", "https://www.deezer.com/track/123"}) {
            helper.assertTrue(bubie.dj().addLink(url) != null, "a streaming service is refused with a reason: " + url);
        }
        helper.assertTrue(Dj.unsupportedSite("https://notyoutube.com/a.mp3") == null, "only those sites, not look-alikes");
        helper.assertTrue(bubie.dj().addLink("https://audius.co/Xsetra/xsetra-fragments") == null, "Audius is taken");
        helper.assertTrue(Dj.linkTitle("https://audius.co/Xsetra/xsetra-fragments").equals("xsetra fragments"), "Audius title from the page");
        helper.assertTrue(Dj.linkTitle("https://drive.google.com/file/d/1AbC/view?usp=sharing").equals("Google Drive"), "Drive title");
        helper.assertTrue(Dj.linkTitle("https://www.dropbox.com/scl/fi/x1/Party_Mix.mp3?rlkey=k&dl=0").equals("Party Mix"), "Dropbox title");

        String dropbox = LinkResolver.rewrite("https://www.dropbox.com/scl/fi/x1/Party_Mix.mp3?rlkey=k&dl=0");
        helper.assertTrue(dropbox.equals("https://www.dropbox.com/scl/fi/x1/Party_Mix.mp3?rlkey=k&dl=1"), "Dropbox downloads: " + dropbox);
        String bare = LinkResolver.rewrite("https://dropbox.com/s/x1/song.ogg");
        helper.assertTrue(bare.equals("https://www.dropbox.com/s/x1/song.ogg?dl=1"), "Dropbox without a query: " + bare);
        String drive = LinkResolver.rewrite("https://drive.google.com/file/d/1AbC-d_E/view?usp=sharing");
        helper.assertTrue(drive.equals("https://drive.usercontent.google.com/download?id=1AbC-d_E&export=download&confirm=t"), "Drive: " + drive);
        String open = LinkResolver.rewrite("https://drive.google.com/open?id=1AbC");
        helper.assertTrue(open.startsWith("https://drive.usercontent.google.com/download?id=1AbC&"), "Drive open link: " + open);
        String github = LinkResolver.rewrite("https://github.com/me/music/blob/main/songs/a.ogg");
        helper.assertTrue(github.equals("https://github.com/me/music/raw/main/songs/a.ogg"), "GitHub file page: " + github);
        String plain = "https://example.com/music/My_Song.mp3";
        helper.assertTrue(LinkResolver.rewrite(plain).equals(plain), "a plain file link is left alone");
        helper.succeed();
    }
}
