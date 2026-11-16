package io.github.larslewerenz.spigottesting;

import io.github.larslewerenz.spigottesting.internal.BotControl;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

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

    /** What the client shows right now besides chat: title, action bar, boss bars, sidebar, tab list. */
    public ClientView view() {
        Bots.requireOffMainThread();
        return ClientView.parse(BotControl.view(name));
    }

    /**
     * Waits up to 10 seconds until what the client shows matches {@code condition} and returns that
     * view, e.g. {@code awaitView(v -> v.sidebar().contains("Kills: 1"))}.
     */
    public ClientView awaitView(Predicate<ClientView> condition) {
        Bots.requireOffMainThread();
        long deadline = System.currentTimeMillis() + MESSAGE_TIMEOUT_MILLIS;
        ClientView view;
        do {
            view = view();
            if (condition.test(view)) {
                return view;
            }
            ServerTest.awaitTicks(1);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError(name + "'s client did not show what was expected within "
                + MESSAGE_TIMEOUT_MILLIS / 1000 + " s. It shows: " + view);
    }

    /** The title of the inventory open on the client (a chest GUI, ...), or null if none is open. */
    public String openInventoryTitle() {
        Bots.requireOffMainThread();
        return BotControl.openInventoryTitle(name);
    }

    /**
     * Waits up to 10 seconds until the client has an inventory open whose title contains
     * {@code text}, and returns the full title (with {@code §} color codes).
     */
    public String awaitInventory(String text) {
        Bots.requireOffMainThread();
        long deadline = System.currentTimeMillis() + MESSAGE_TIMEOUT_MILLIS;
        String title;
        do {
            title = BotControl.openInventoryTitle(name);
            if (title != null && title.contains(text)) {
                return title;
            }
            ServerTest.awaitTicks(1);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError(name + " has no inventory titled \"" + text + "\" open after "
                + MESSAGE_TIMEOUT_MILLIS / 1000 + " s (open: " + title + ")");
    }

    /** Left-clicks a slot of the open inventory. See {@link #click(int, ClickType)}. */
    public void click(int slot) {
        click(slot, ClickType.LEFT);
    }

    /**
     * Clicks a slot of the inventory open on the client, sent as a real click packet, so the server
     * fires {@code InventoryClickEvent} as for a player. {@code slot} is the raw slot number
     * (top inventory first, then the player's inventory), like {@code InventoryClickEvent#getRawSlot}.
     * Supported types: {@code LEFT}, {@code RIGHT}, {@code SHIFT_LEFT}, {@code SHIFT_RIGHT}.
     * Returns right away; the server handles the click on one of its next ticks.
     */
    public void click(int slot, ClickType type) {
        Bots.requireOffMainThread();
        switch (type) {
            case LEFT:
            case RIGHT:
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                BotControl.click(name, slot, type.name());
                break;
            default:
                throw new UnsupportedOperationException("Bots can't do " + type + " clicks yet");
        }
    }

    /** Closes the inventory open on the client, like pressing Escape. */
    public void closeInventory() {
        Bots.requireOffMainThread();
        BotControl.closeInventory(name);
    }

    /**
     * Waits up to 10 seconds for the server to disconnect this bot (a kick, a ban, ...) and returns
     * the reason it sent, with {@code §} color codes.
     */
    public String awaitKick() {
        Bots.requireOffMainThread();
        long deadline = System.currentTimeMillis() + MESSAGE_TIMEOUT_MILLIS;
        do {
            String reason = BotControl.kickReason(name);
            if (reason != null) {
                Bots.awaitOffline(name);
                Bots.forget(this);
                return reason;
            }
            ServerTest.awaitTicks(1);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError(name + " was not kicked within " + MESSAGE_TIMEOUT_MILLIS / 1000 + " s");
    }

    /**
     * Teleports the player and waits until the client has arrived. Prefer this to
     * {@code player().teleport(...)}: until the client confirms a teleport, the server ignores its
     * block clicks and movement.
     */
    public void teleport(Location target) {
        Bots.requireOffMainThread();
        double teleportsBefore = BotControl.position(name)[3];
        if (!ServerTest.sync(() -> player.teleport(target))) {
            throw new IllegalStateException("The server refused to teleport " + name + " to " + target);
        }
        long deadline = System.currentTimeMillis() + MESSAGE_TIMEOUT_MILLIS;
        while (true) {
            // A new teleport, not just the right place: the bot may have been there already.
            double[] at = BotControl.position(name);
            double dx = at[0] - target.getX();
            double dy = at[1] - target.getY();
            double dz = at[2] - target.getZ();
            if (at[3] > teleportsBefore && dx * dx + dy * dy + dz * dz <= 0.01) {
                break;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(name + "'s client did not arrive at " + target + "; it is at "
                        + at[0] + " " + at[1] + " " + at[2]);
            }
            ServerTest.awaitTicks(1);
        }
        // The client confirmed on arrival; let the server handle the confirmation.
        ServerTest.awaitTicks(2);
    }

    /**
     * Walks to {@code target} in a straight line at about walking speed, sending movement packets
     * like a client, so the server fires {@code PlayerMoveEvent}s along the way. Waits until the
     * server has the player there. No pathfinding: keep the way clear.
     */
    public void moveTo(Location target) {
        Bots.requireOffMainThread();
        if (!target.getWorld().equals(ServerTest.sync(() -> player.getWorld()))) {
            throw new IllegalArgumentException(name + " can only walk within its world (" + player.getWorld().getName() + ")");
        }
        BotControl.move(name, target.getX(), target.getY(), target.getZ());
        long deadline = System.currentTimeMillis() + MESSAGE_TIMEOUT_MILLIS;
        while (true) {
            Location at = ServerTest.sync(() -> player.getLocation());
            if (at.distanceSquared(target) <= 0.01) {
                return;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError(name + " did not arrive at " + target + "; the server has it at " + at);
            }
            ServerTest.awaitTicks(1);
        }
    }

    /**
     * Breaks a block like a player holding the attack button on it: at once in creative mode, in
     * survival once the player has mined it long enough (the server decides, as for real players).
     * The block must be within reach. Returns right away; await the result, e.g. with
     * {@link ServerTest#recordEvents(Class)} and {@code BlockBreakEvent}.
     */
    public void breakBlock(Block block) {
        Bots.requireOffMainThread();
        boolean creative = ServerTest.sync(() -> player.getGameMode() == GameMode.CREATIVE);
        BotControl.breakBlock(name, block.getX(), block.getY(), block.getZ(), creative);
    }

    /**
     * Right-clicks a face of a block with the item in hand, like a player: places a block next to
     * it, opens a door or chest, flips a lever, ... The block must be within reach. Returns right
     * away; the server handles the click on one of its next ticks.
     */
    public void rightClickBlock(Block block, BlockFace face) {
        Bots.requireOffMainThread();
        switch (face) {
            case UP:
            case DOWN:
            case NORTH:
            case SOUTH:
            case EAST:
            case WEST:
                BotControl.useItemOn(name, block.getX(), block.getY(), block.getZ(), face.name());
                break;
            default:
                throw new IllegalArgumentException("Only UP, DOWN, NORTH, SOUTH, EAST and WEST faces can be clicked, not " + face);
        }
    }

    /** Selects a hotbar slot (0-8) like pressing 1-9, and waits until the server has it. */
    public void selectHotbarSlot(int slot) {
        Bots.requireOffMainThread();
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("Hotbar slots are 0-8, not " + slot);
        }
        BotControl.selectHotbarSlot(name, slot);
        long deadline = System.currentTimeMillis() + MESSAGE_TIMEOUT_MILLIS;
        while (ServerTest.sync(() -> player.getInventory().getHeldItemSlot()) != slot) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("The server did not select hotbar slot " + slot + " for " + name);
            }
            ServerTest.awaitTicks(1);
        }
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
