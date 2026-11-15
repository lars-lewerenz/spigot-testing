package io.github.larslewerenz.greeter;

import static io.github.larslewerenz.spigottesting.ServerTest.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.larslewerenz.spigottesting.Bot;
import io.github.larslewerenz.spigottesting.Bots;
import io.github.larslewerenz.spigottesting.OffMainThread;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

/**
 * Tests with fake players: bot clients that connect to the server over the real network protocol.
 * Waiting for the server needs it to tick, so these tests run off the main thread.
 */
@OffMainThread
class GreeterPlayerTest {

    @Test
    void joiningPlayersAreWelcomed() {
        Bot alex = Bots.join("Alex");

        assertEquals(ChatColor.YELLOW + "Welcome, Alex!", alex.awaitMessage("Welcome"));
    }

    @Test
    void playersCanUseTheGreetCommand() {
        Bot alex = Bots.join("Alex");

        alex.chat("/greet Steve");

        assertEquals(ChatColor.GREEN + "Hello, Steve!", alex.awaitMessage("Hello"));
    }

    @Test
    void chatReachesOtherPlayers() {
        Bot alex = Bots.join("Alex");
        Bot bob = Bots.join("Bob");

        alex.chat("hi bob");

        assertTrue(bob.awaitMessage("hi bob").contains("Alex"));
    }

    @Test
    void botsAreRealPlayersOnTheServer() {
        Bot alex = Bots.join("Alex");

        assertEquals("Alex", sync(() -> alex.player().getName()));
        assertTrue(sync(() -> Bukkit.getOnlinePlayers().contains(alex.player())));

        alex.quit();

        assertFalse(alex.isOnline());
    }
}
