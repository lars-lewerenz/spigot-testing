package io.github.larslewerenz.greeter;

import static io.github.larslewerenz.spigottesting.ServerTest.awaitTicks;
import static io.github.larslewerenz.spigottesting.ServerTest.recordEvents;
import static io.github.larslewerenz.spigottesting.ServerTest.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.larslewerenz.spigottesting.Bot;
import io.github.larslewerenz.spigottesting.Bots;
import io.github.larslewerenz.spigottesting.EventRecorder;
import io.github.larslewerenz.spigottesting.OffMainThread;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** Bots walking, building and fighting, like players: the spawn area is protected (the world is flat and empty). */
@OffMainThread
class GreeterSpawnTest {

    @Test
    void walkingOutOfTheSpawnAreaIsAnnounced() {
        Bot alex = joinAtSpawn("Alex");
        Location spawn = sync(() -> alex.player().getWorld().getSpawnLocation());

        alex.moveTo(outsideSpawn(alex, spawn));

        assertEquals(ChatColor.GRAY + "You left the spawn area.", alex.awaitMessage("left the spawn"));
    }

    @Test
    void nobodyBuildsAtSpawn() {
        Bot alex = joinAtSpawn("Alex");
        Block ground = groundNextTo(alex);
        EventRecorder<BlockPlaceEvent> places = recordEvents(BlockPlaceEvent.class);
        EventRecorder<BlockBreakEvent> breaks = recordEvents(BlockBreakEvent.class);
        holdDirt(alex);

        alex.rightClickBlock(ground, BlockFace.UP);
        alex.breakBlock(ground);

        assertTrue(places.awaitEvent().isCancelled());
        assertTrue(breaks.awaitEvent().isCancelled());
        assertEquals(ChatColor.RED + "You can't build at spawn.", alex.awaitMessage("can't build"));
        awaitTicks(2);
        assertEquals(Material.AIR, sync(() -> ground.getRelative(BlockFace.UP).getType()));
        assertTrue(sync(() -> ground.getType() != Material.AIR));
    }

    @Test
    void playersBuildAwayFromSpawn() {
        Bot alex = joinAtSpawn("Alex");
        Location spawn = sync(() -> alex.player().getWorld().getSpawnLocation());
        alex.moveTo(outsideSpawn(alex, spawn));
        Block ground = groundNextTo(alex);
        Block above = ground.getRelative(BlockFace.UP);
        EventRecorder<BlockBreakEvent> breaks = recordEvents(BlockBreakEvent.class);
        holdDirt(alex);

        alex.rightClickBlock(ground, BlockFace.UP);
        awaitBlock(above, Material.DIRT);
        // In survival, breaking takes as long as it does for a player (dirt by hand: under a second).
        alex.breakBlock(above);
        breaks.awaitEvent(event -> event.getBlock().equals(above));
        awaitBlock(above, Material.AIR);
    }

    @Test
    void nobodyFightsAtSpawn() {
        Bot alex = joinAtSpawn("Alex");
        Bot bob = joinNextTo(alex, "Bob");
        EventRecorder<EntityDamageByEntityEvent> hits = recordEvents(EntityDamageByEntityEvent.class);

        alex.attack(bob.player());

        assertTrue(hits.awaitEvent(hit -> hit.getEntity().getName().equals("Bob")).isCancelled());
        assertEquals(ChatColor.RED + "No fighting at spawn.", alex.awaitMessage("No fighting"));
    }

    @Test
    void playersFightAwayFromSpawn() {
        Bot alex = joinAtSpawn("Alex");
        alex.teleport(outsideSpawn(alex, sync(() -> alex.player().getWorld().getSpawnLocation())));
        Bot bob = joinNextTo(alex, "Bob");
        EventRecorder<EntityDamageByEntityEvent> hits = recordEvents(EntityDamageByEntityEvent.class);

        alex.attack(bob.player());

        EntityDamageByEntityEvent hit = hits.awaitEvent(event -> event.getEntity().getName().equals("Bob"));
        assertFalse(hit.isCancelled());
        assertTrue(hit.getFinalDamage() > 0);
    }

    /**
     * Puts a second player within reach of {@code other}. On older versions players can't be hurt
     * for 3 seconds (60 ticks) after joining, so this waits that out.
     */
    private static Bot joinNextTo(Bot other, String name) {
        Bot bot = Bots.join(name);
        bot.teleport(sync(() -> other.player().getLocation().add(1.5, 0, 0)));
        awaitTicks(60);
        return bot;
    }

    /** Players come back where they left, so put the bot at the spawn first. */
    private static Bot joinAtSpawn(String name) {
        Bot bot = Bots.join(name);
        bot.teleport(sync(() -> bot.player().getWorld().getSpawnLocation()));
        return bot;
    }

    /** 20 blocks east of spawn, at the height the bot stands on. */
    private static Location outsideSpawn(Bot bot, Location spawn) {
        Location at = sync(() -> bot.player().getLocation());
        return new Location(at.getWorld(), spawn.getBlockX() + 20.5, at.getY(), at.getZ());
    }

    /** The ground block two blocks east of the bot: within reach, but not under its feet. */
    private static Block groundNextTo(Bot bot) {
        return sync(() -> bot.player().getLocation().getBlock().getRelative(2, -1, 0));
    }

    private static void holdDirt(Bot bot) {
        sync(() -> bot.player().getInventory().setItem(0, new ItemStack(Material.DIRT, 1)));
        bot.selectHotbarSlot(0);
    }

    private static void awaitBlock(Block block, Material type) {
        for (int i = 0; i < 100 && sync(block::getType) != type; i++) {
            awaitTicks(1);
        }
        assertEquals(type, sync(block::getType));
    }
}
