package io.github.larslewerenz.spigottesting;

import io.github.larslewerenz.spigottesting.internal.BotControl;
import io.github.larslewerenz.spigottesting.internal.TestLog;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

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
        return join(name, null);
    }

    /**
     * Connects a bot that plays on another client version than the server's, e.g.
     * {@code join("Alex", "1.8.8")} on a 26.3 server, and waits until the player is online. The
     * server must accept that client, like a real one would: with ViaVersion installed (and
     * ViaBackwards for older clients, ViaRewind for 1.7 and 1.8), see the README. Everything the
     * bot sees and sends goes through that translation, so tests see what players on that version
     * see. Only callable from tests annotated with {@link OffMainThread}.
     *
     * @param clientVersion a Minecraft version such as {@code "1.8.8"} or {@code "1.20.4"}, or null
     *     for the server's own
     */
    public static Bot join(String name, String clientVersion) {
        requireOffMainThread();
        Player player;
        try (EventRecorder<PlayerJoinEvent> joins = EventRecorder.start(PlayerJoinEvent.class)) {
            BotControl.join(name, clientVersion);
            long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
            while ((player = ServerTest.sync(() -> Bukkit.getPlayerExact(name))) == null) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException(name + " connected, but the server did not report the player online");
                }
                ServerTest.awaitTicks(1);
            }
            // Let the server process the client's spawn confirmation before the test talks to it.
            ServerTest.awaitTicks(2);
            warnAboutRepeatedJoins(name, joins);
        }
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

    /**
     * A bot's connection can break inside ViaProxy after the server has already let the player in
     * (rarely, on servers older than 1.20.2). The bot then reconnects, and plugins see the player
     * join twice: say so, or a test about first joins fails without an explanation.
     */
    private static void warnAboutRepeatedJoins(String name, EventRecorder<PlayerJoinEvent> joins) {
        long count = joins.events().stream().filter(event -> event.getPlayer().getName().equals(name)).count();
        if (count > 1) {
            TestLog.warning(name + " joined the server " + count + " times: the first connection broke inside"
                    + " ViaProxy after the server had let the player in, and the bot reconnected. Plugins saw"
                    + " every join, so anything that counts joins or first visits is off in this test.");
        }
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
