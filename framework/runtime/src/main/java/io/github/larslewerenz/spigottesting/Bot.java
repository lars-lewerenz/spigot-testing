package io.github.larslewerenz.spigottesting;

import io.github.larslewerenz.spigottesting.internal.BotControl;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * A fake player: a bot client connected to the test server over the real network protocol.
 * Created with {@link Bots#join(String)}.
 *
 * <p>Waiting for the server (joining, quitting, receiving messages) needs the server to tick, so
 * bots can only be used from tests annotated with {@link OffMainThread}. Use
 * {@link ServerTest#sync(java.util.concurrent.Callable)} to work with {@link #player()}.
 */
public final class Bot {

    private static final long MESSAGE_TIMEOUT_MILLIS = 10_000;

    private final String name;
    private final Player player;

    Bot(String name, Player player) {
        this.name = name;
        this.player = player;
    }

    public String name() {
        return name;
    }

    /** The server-side Bukkit player. Use it on the main thread, e.g. inside {@code ServerTest.sync}. */
    public Player player() {
        return player;
    }

    /**
     * Sends a chat message from the client, as if the player typed it. Text starting with {@code /}
     * is sent as a command. Returns right away; the server handles it on one of its next ticks.
     */
    public void chat(String text) {
        Bots.requireOffMainThread();
        BotControl.chat(name, text);
    }

    /** All chat and system messages the client has received so far, with {@code §} color codes. */
    public List<String> messages() {
        Bots.requireOffMainThread();
        return BotControl.messages(name);
    }

    /** Waits up to 10 seconds for a received message containing {@code text} and returns it. */
    public String awaitMessage(String text) {
        return awaitMessage(m -> m.contains(text), "containing \"" + text + "\"");
    }

    /** Waits up to 10 seconds for a received message that matches {@code condition} and returns it. */
    public String awaitMessage(Predicate<String> condition) {
        return awaitMessage(condition, "matching the condition");
    }

    private String awaitMessage(Predicate<String> condition, String description) {
        Bots.requireOffMainThread();
        long deadline = System.currentTimeMillis() + MESSAGE_TIMEOUT_MILLIS;
        List<String> messages;
        do {
            messages = BotControl.messages(name);
            for (String message : messages) {
                if (condition.test(message)) {
                    return message;
                }
            }
            ServerTest.awaitTicks(1);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError(name + " received no message " + description + " within "
                + MESSAGE_TIMEOUT_MILLIS / 1000 + " s. Received: " + messages);
    }

    /** Disconnects the client and waits until the server has removed the player. */
    public void quit() {
        Bots.requireOffMainThread();
        BotControl.quit(name);
        Bots.awaitOffline(name);
        Bots.forget(this);
    }

    /** Whether the player is (still) online on the server. */
    public boolean isOnline() {
        return ServerTest.sync(() -> Bukkit.getPlayerExact(name) != null);
    }

    @Override
    public String toString() {
        return "Bot[" + name + "]";
    }
}
