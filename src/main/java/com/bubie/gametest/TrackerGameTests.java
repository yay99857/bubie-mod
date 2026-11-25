package com.bubie.gametest;

import com.bubie.Bubie;
import com.bubie.data.BubieRegistry;
import com.bubie.entity.BubieEntity;
import com.bubie.entity.Mode;
import com.bubie.item.WirelessStationItem;
import com.bubie.network.StationBubiesPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.List;

/** The Station's tracker (where its Bubies are, kept in the world's registry) and the Wireless Station linked to it. */
public class TrackerGameTests {
    private static final BlockPos STATION = new BlockPos(7, 1, 7);

    @BubieTest(template = BubieGameTests.FIELD, timeoutTicks = 200)
    public static void stationTracksTheBubiesLivingThere(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        helper.setBlock(STATION, Bubie.STATION.get());
        BlockPos station = helper.absolutePos(STATION);
        String dimension = helper.getLevel().dimension().identifier().toString();
        BubieEntity bubie = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(4, 1, 4));
        bubie.setEnergy(40000);
        bubie.setCustomName(Component.literal("Tracked"));
        bubie.setHome(station);
        bubie.setMode(Mode.STAY);
        BubieEntity stranger = helper.spawn(Bubie.BUBIE_ENTITY.get(), new BlockPos(10, 1, 10)); // no home: not tracked
        stranger.setMode(Mode.STAY);
        BubieRegistry registry = BubieRegistry.get(helper.getLevel().getServer());
        helper.startSequence().thenWaitUntil(() -> {
            List<BubieRegistry.Sighting> seen = registry.livingAt(dimension, station);
            helper.assertTrue(seen.size() == 1, "one Bubie lives at the Station, the registry has " + seen.size());
            BubieRegistry.Sighting s = seen.get(0);
            helper.assertTrue(s.name().equals("Tracked"), "by its name, got " + s.name());
            helper.assertTrue(BlockPos.of(s.pos()).equals(bubie.blockPosition()), "where it stands, got " + BlockPos.of(s.pos()));
            helper.assertTrue(s.doing() == -1 - Mode.STAY.ordinal(), "staying, got " + s.doing());
            // what the Station's screen and the Wireless Station are sent
            List<StationBubiesPayload.Entry> sent = StationBubiesPayload.of(helper.getLevel().getServer(),
                    helper.getLevel().dimension(), station, helper.getLevel().dimension()).bubies();
            helper.assertTrue(sent.size() == 1 && sent.get(0).blockPos().equals(bubie.blockPosition()) && !sent.get(0).elsewhere(),
                    "the tracker shows it here, in this dimension");
        }).thenExecute(bubie::discard).thenExecuteAfter(1, () ->
                helper.assertTrue(registry.livingAt(dimension, station).isEmpty(), "a Bubie that is gone is no longer tracked")
        ).thenSucceed();
    }

    @BubieTest(template = BubieGameTests.FIELD, timeoutTicks = 40)
    public static void wirelessStationLinksWithAShiftClick(GameTestHelper helper) {
        BubieGameTests.floor(helper, Blocks.STONE);
        helper.setBlock(STATION, Bubie.STATION.get());
        BlockPos station = helper.absolutePos(STATION);
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "WirelessTester"));
        ItemStack handheld = new ItemStack(Bubie.WIRELESS_STATION.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, handheld);
        helper.assertTrue(WirelessStationItem.link(handheld) == null && WirelessStationItem.held(player) == null, "new: not linked");

        UseOnContext click = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(station), Direction.UP, station, false));
        handheld.useOn(click);
        helper.assertTrue(WirelessStationItem.link(handheld) == null, "a plain click on the Station does not link");
        player.setShiftKeyDown(true);
        handheld.useOn(click);
        player.setShiftKeyDown(false);
        WirelessStationItem.Link link = WirelessStationItem.held(player);
        helper.assertTrue(link != null && link.pos().equals(station) && link.dimension() == helper.getLevel().dimension(),
                "shift + click links it to that Station, got " + link);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }
}
