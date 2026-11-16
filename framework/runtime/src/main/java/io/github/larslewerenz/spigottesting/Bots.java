package io.github.larslewerenz.spigottesting;

import io.github.larslewerenz.spigottesting.internal.BotControl;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Fake players for server tests. {@link #join(String)} connects a bot client to the test server
 * over the real network protocol (offline mode), so the server goes through its normal login
 * and fires the usual events ({@code PlayerLoginEvent}, {@code PlayerJoinEvent}, ...).
 *
 * <p>Bots still online at the end of a test are disconnected automatically.
 */
public final class Bots {

    private static final long TIMEOUT_MILLIS = 30_000;
    private static final List<Bot> ONLINE = new CopyOnWriteArrayList<>();

    private Bots() {
    }

    /**
     * Connects a bot with the given name (1-16 letters, digits or {@code _}) and waits until the
     * player is online. Only callable from tests annotated with {@link OffMainThread}.
     */
    public static Bot join(String name) {
        requireOffMainThread();
        BotControl.join(name);
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        Player player;
        while ((player = ServerTest.sync(() -> Bukkit.getPlayerExact(name))) == null) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException(name + " connected, but the server did not report the player online");
            }
            ServerTest.awaitTicks(1);
        }
        // Let the server process the client's spawn confirmation before the test talks to it.
        ServerTest.awaitTicks(2);
        Bot bot = new Bot(name, player);
        ONLINE.add(bot);
        return bot;
    }

    /**
     * Connects a bot that the server is expected to turn away (a ban, the whitelist, a plugin
     * disallowing the login, ...) and returns the reason the server sent, with {@code §} color
     * codes. Fails if the bot gets in. Only callable from tests annotated with {@link OffMainThread}.
     */
    public static String expectRejection(String name) {
        requireOffMainThread();
        String reason = BotControl.joinExpectingRejection(name);
        if (reason == null) {
            awaitOffline(name);
            throw new AssertionError(name + " was let in, but the server should have refused the login");
        }
        return reason;
    }

    /** Disconnects every bot that is still online. Called automatically after each test. */
    public static void quitAll() {
        for (Bot bot : new ArrayList<>(ONLINE)) {
            bot.quit();
        }
    }

    static void forget(Bot bot) {
        ONLINE.remove(bot);
    }

    static void awaitOffline(String name) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (ServerTest.sync(() -> Bukkit.getPlayerExact(name)) != null) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException(name + " disconnected, but the server still reports the player online");
            }
            ServerTest.awaitTicks(1);
        }
    }

    static void requireOffMainThread() {
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "Bots need the server to tick while the test waits; annotate the test with @OffMainThread");
        }
    }
}
