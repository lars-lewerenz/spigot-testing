package io.github.larslewerenz.spigottesting.bots;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAttackPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.cloudburstmc.math.vector.Vector3d;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;

/** One bot client: an offline-mode player connected over the real network protocol. */
final class Bot {

    private static final long JOIN_TIMEOUT_SECONDS = 10;
    private static final int JOIN_ATTEMPTS = 3;
    /** How ViaProxy kicks a client when it fails internally (not when the server rejects it). */
    private static final String VIAPROXY_ERROR = "An unhandled error occurred in your connection";
    /** The client version that matches the server's, detected by ViaProxy. */
    static final String SERVER_VERSION = "auto";
    /** Separates the parts of the handshake address for ViaProxy. */
    private static final char BEL = 7;

    private final String name;
    private final ClientSession session;
    private final List<String> messages = new ArrayList<>();
    private final ClientView view = new ClientView();
    private final CompletableFuture<Void> joined = new CompletableFuture<>();
    private final List<Runnable> disconnectListeners = new ArrayList<>();
    private boolean loaded;
    /** The container the server has opened for this client, or -1. */
    private int containerId = -1;
    private String containerTitle;
    /** The server's last state id; clicks must send it back. */
    private int stateId;
    /** Sends every bot's "tick end", like a client does after each of its ticks (every 50 ms). */
    private static final ScheduledExecutorService TICKER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bot-ticks");
        thread.setDaemon(true);
        return thread;
    });

    /** Blocks per movement packet, sent every 50 ms: about a player's walking speed. */
    private static final double STEP = 0.2;
    /** Where the server last put this player (x, y, z, yaw, pitch), or null before it spawned. */
    private double[] position;
    /** How many teleports the client has confirmed. */
    private int teleports;
    /** Numbers block interactions, as the server expects (1.19+; ViaProxy handles older ones). */
    private final AtomicInteger sequence = new AtomicInteger();
    /** Set when the test disconnects the bot itself, so that is not taken for a kick. */
    private volatile boolean quitting;
    /** Why the server disconnected this bot (a kick), or null while it is online. */
    private volatile String kickReason;

    private Bot(String name, ClientSession session) {
        this.name = name;
        this.session = session;
    }

    /**
     * Connects and returns once the player has spawned (its spawn position is confirmed); some
     * versions ignore commands from a player before that.
     *
     * <p>Two ViaProxy hiccups are retried: a quick reconnect with the same name occasionally
     * hangs in the configuration phase, and a rare race in ViaProxy's login handling ends the
     * connection with its "unhandled error" message. Rejections by the server itself (bans,
     * whitelist, a plugin cancelling the login) are not retried; they fail the join right away.
     *
     * <p>{@code version} is the client version ViaProxy presents to the server ({@code auto}: the
     * server's own). It travels in the handshake address, in the format of ViaProxy's internal
     * wildcard domain handling: {@code original-address BEL target-address BEL version}.
     */
    static Bot connect(String name, String host, int port, int serverPort, String version) throws Exception {
        for (int attempt = 1; ; attempt++) {
            ClientSession session = ClientNetworkSessionFactory.factory()
                    .setAddress(host, port)
                    .setProtocol(new MinecraftProtocol(name))
                    .create();
            session.setFlag(MinecraftConstants.CLIENT_HOST, "127.0.0.1" + BEL + "127.0.0.1:" + serverPort + BEL + version);
            Bot bot = new Bot(name, session);
            session.addListener(bot.new Listener());
            session.connect(true);
            try {
                bot.joined.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return bot;
            } catch (ExecutionException e) {
                String reason = BotController.oneLine(e.getCause());
                if (e.getCause() instanceof Rejected rejected && !reason.contains(VIAPROXY_ERROR)) {
                    throw rejected;
                }
                if (!reason.contains(VIAPROXY_ERROR) || attempt == JOIN_ATTEMPTS) {
                    throw new IllegalStateException(name + " could not join: " + reason, e.getCause());
                }
                System.out.println(name + " hit a ViaProxy error while joining, retrying: " + reason);
            } catch (TimeoutException e) {
                session.disconnect("Join timed out");
                if (attempt == JOIN_ATTEMPTS) {
                    throw new IllegalStateException(name + " did not join within " + JOIN_TIMEOUT_SECONDS + " s ("
                            + JOIN_ATTEMPTS + " attempts)");
                }
                System.out.println(name + " did not join within " + JOIN_TIMEOUT_SECONDS + " s, retrying");
            }
        }
    }

    void chat(String text) {
        if (text.startsWith("/")) {
            session.send(new ServerboundChatCommandPacket(text.substring(1)));
        } else {
            // Unsigned chat: the test servers run in offline mode without secure profiles.
            session.send(new ServerboundChatPacket(text, System.currentTimeMillis(), 0L, null, 0, new BitSet(), 0));
        }
    }

    /** Applies a teleport from the server (parts of it may be relative) and returns the new position. */
    private synchronized double[] teleported(ClientboundPlayerPositionPacket packet) {
        double[] old = position == null ? new double[5] : position;
        List<PositionElement> relative = packet.getRelatives();
        teleports++;
        position = new double[] {
            packet.getPosition().getX() + (relative.contains(PositionElement.X) ? old[0] : 0),
            packet.getPosition().getY() + (relative.contains(PositionElement.Y) ? old[1] : 0),
            packet.getPosition().getZ() + (relative.contains(PositionElement.Z) ? old[2] : 0),
            packet.getYRot() + (relative.contains(PositionElement.Y_ROT) ? old[3] : 0),
            packet.getXRot() + (relative.contains(PositionElement.X_ROT) ? old[4] : 0),
        };
        return position.clone();
    }

    /**
     * Where the client thinks it is (x, y, z), as of the last teleport it confirmed or step it sent,
     * and how many teleports it has confirmed so far.
     */
    synchronized double[] clientPosition() {
        double[] at = position();
        return new double[] {at[0], at[1], at[2], teleports};
    }

    private synchronized double[] position() {
        if (position == null) {
            throw new IllegalStateException(name + " has not spawned yet");
        }
        return position.clone();
    }

    private synchronized void moved(double[] to) {
        position = to;
    }

    /**
     * Walks to the given position in a straight line at about walking speed, sending movement
     * packets like a client. No pathfinding: if something is in the way, the server pushes the
     * player back and this gives up.
     */
    void moveTo(double x, double y, double z) throws InterruptedException {
        double[] start = position();
        double distance = Math.sqrt(square(x - start[0]) + square(y - start[1]) + square(z - start[2]));
        int maxSteps = (int) (distance / STEP) + 40;
        for (int i = 0; i < maxSteps; i++) {
            double[] from = position();
            double dx = x - from[0];
            double dy = y - from[1];
            double dz = z - from[2];
            double left = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (left < 1e-6) {
                return;
            }
            double f = Math.min(1, STEP / left);
            double[] to = {from[0] + dx * f, from[1] + dy * f, from[2] + dz * f, from[3], from[4]};
            if (!session.isConnected()) {
                throw new IllegalStateException(name + " was disconnected while walking: " + kickReason);
            }
            moved(to);
            session.send(new ServerboundMovePlayerPosPacket(true, false, to[0], to[1], to[2]));
            // One position per client tick: 26.3+ disconnects clients that send two.
            session.send(ServerboundClientTickEndPacket.INSTANCE);
            Thread.sleep(50);
        }
        throw new IllegalStateException(name + " could not get to " + x + " " + y + " " + z
                + " (something in the way?); it is at " + java.util.Arrays.toString(position()));
    }

    private static double square(double d) {
        return d * d;
    }

    /**
     * Starts breaking the block, like holding the attack button on it. In creative mode it breaks
     * at once; in survival the server breaks it once the player has mined it long enough.
     */
    void breakBlock(int x, int y, int z, boolean creative) {
        Vector3i block = Vector3i.from(x, y, z);
        session.send(new ServerboundPlayerActionPacket(PlayerAction.START_DIGGING, block, Direction.UP, sequence.incrementAndGet()));
        if (!creative) {
            // Releasing early is fine: the server finishes the block when its mining time is up.
            session.send(new ServerboundPlayerActionPacket(PlayerAction.FINISH_DIGGING, block, Direction.UP, sequence.incrementAndGet()));
        }
    }

    /** Right-clicks a face of the block with the item in hand: places a block, opens a door, ... */
    void useItemOn(int x, int y, int z, String face) {
        Direction direction = Direction.valueOf(face);
        // Where on the face the click lands: its center.
        float cx = direction == Direction.EAST ? 1 : direction == Direction.WEST ? 0 : 0.5f;
        float cy = direction == Direction.UP ? 1 : direction == Direction.DOWN ? 0 : 0.5f;
        float cz = direction == Direction.SOUTH ? 1 : direction == Direction.NORTH ? 0 : 0.5f;
        session.send(new ServerboundUseItemOnPacket(Vector3i.from(x, y, z), direction, Hand.MAIN_HAND, cx, cy, cz,
                false, false, sequence.incrementAndGet()));
    }

    /** Hits the entity with the item in hand, like a left click on it. */
    void attack(int entityId) {
        session.send(new ServerboundAttackPacket(entityId));
    }

    /**
     * Right-clicks the entity with the main hand. The click lands half a block above the entity's
     * feet (servers report it as the clicked position of {@code PlayerInteractAtEntityEvent}).
     */
    void interact(int entityId) {
        session.send(new ServerboundInteractPacket(entityId, Hand.MAIN_HAND, Vector3d.from(0, 0.5, 0), false));
    }

    /** Right-clicks with the item in hand while looking where the player looks (not at a block). */
    void useItem() {
        double[] at = position();
        session.send(new ServerboundUseItemPacket(Hand.MAIN_HAND, sequence.incrementAndGet(), (float) at[3], (float) at[4]));
    }

    /** Drops one item of the held stack (Q), or the whole stack (Ctrl+Q). */
    void dropItem(boolean wholeStack) {
        playerAction(wholeStack ? PlayerAction.DROP_ITEM_STACK : PlayerAction.DROP_ITEM);
    }

    /** Swaps the items in the main and off hand (F). */
    void swapHands() {
        playerAction(PlayerAction.SWAP_HANDS);
    }

    private void playerAction(PlayerAction action) {
        // Like the client: these actions are not about a block, so position and face are unused.
        session.send(new ServerboundPlayerActionPacket(action, Vector3i.ZERO, Direction.DOWN, 0));
    }

    /** Selects a hotbar slot (0-8), like pressing 1-9. */
    void selectHotbarSlot(int slot) {
        session.send(new ServerboundSetCarriedItemPacket(slot));
    }

    /** The title of the open container (a chest GUI, ...), or null if none is open. */
    synchronized String openContainerTitle() {
        return containerId < 0 ? null : containerTitle;
    }

    /**
     * Clicks a slot of the open container like a player would. Slot numbers are the container's
     * raw slots (the top inventory first, then the player's inventory), as in Bukkit.
     */
    synchronized void click(int slot, String type) {
        if (containerId < 0) {
            throw new IllegalStateException(name + " has no inventory open");
        }
        ContainerActionType action;
        ContainerAction param;
        switch (type) {
            case "LEFT" -> { action = ContainerActionType.CLICK_ITEM; param = ClickItemAction.LEFT_CLICK; }
            case "RIGHT" -> { action = ContainerActionType.CLICK_ITEM; param = ClickItemAction.RIGHT_CLICK; }
            case "SHIFT_LEFT" -> { action = ContainerActionType.SHIFT_CLICK_ITEM; param = ShiftClickItemAction.LEFT_CLICK; }
            case "SHIFT_RIGHT" -> { action = ContainerActionType.SHIFT_CLICK_ITEM; param = ShiftClickItemAction.RIGHT_CLICK; }
            default -> throw new IllegalArgumentException("Unsupported click type " + type);
        }
        // No predicted result: the server works out the outcome and sends the real contents back.
        session.send(new ServerboundContainerClickPacket(containerId, stateId, slot, action, param, null, Map.of()));
    }

    synchronized void closeContainer() {
        if (containerId >= 0) {
            session.send(new ServerboundContainerClosePacket(containerId));
            containerId = -1;
        }
    }

    private synchronized void containerOpened(int id, String title) {
        containerId = id;
        containerTitle = title;
    }

    private synchronized void containerState(int id, int state) {
        if (id == containerId) {
            stateId = state;
        }
    }

    private synchronized void containerClosed() {
        containerId = -1;
    }

    /** Titles, action bar, boss bars, sidebar and tab list, as described by {@link ClientView#describe()}. */
    List<String> view() {
        return view.describe();
    }

    synchronized List<String> messages() {
        return new ArrayList<>(messages);
    }

    /** Why the server disconnected this bot, or null if it is online or the test disconnected it. */
    String kickReason() {
        return kickReason;
    }

    void quit() {
        quitting = true;
        if (session.isConnected()) {
            session.disconnect("Test finished");
        }
    }

    synchronized void onDisconnect(Runnable listener) {
        disconnectListeners.add(listener);
    }

    private synchronized void record(String message) {
        messages.add(message);
    }

    private final class Listener extends SessionAdapter {

        @Override
        public void packetReceived(Session session, Packet packet) {
            if (view.handle(packet)) {
                return;
            }
            if (packet instanceof ClientboundPlayerPositionPacket position) {
                double[] at = teleported(position);
                // Confirm the server-side teleports, like a real client. Newer servers take the
                // position from the confirmation, so it must be the absolute one.
                session.send(new ServerboundAcceptTeleportationPacket(position.getId(), at[0], at[1], at[2],
                        (float) at[3], (float) at[4]));
                if (!loaded) {
                    loaded = true;
                    session.send(ServerboundPlayerLoadedPacket.INSTANCE);
                    // Like a client after each of its ticks. 26.3+ servers count movement per
                    // client tick and reset their movement checks when a tick ends.
                    ScheduledFuture<?> ticks = TICKER.scheduleAtFixedRate(() -> {
                        if (session.isConnected()) {
                            session.send(ServerboundClientTickEndPacket.INSTANCE);
                        }
                    }, 50, 50, TimeUnit.MILLISECONDS);
                    onDisconnect(() -> ticks.cancel(false));
                    // Only now is the player fully in the game: some versions ignore commands
                    // from a player that has not confirmed its spawn position yet.
                    joined.complete(null);
                }
            } else if (packet instanceof ClientboundOpenScreenPacket screen) {
                containerOpened(screen.getContainerId(), ChatText.legacy(screen.getTitle()));
            } else if (packet instanceof ClientboundContainerSetContentPacket content) {
                containerState(content.getContainerId(), content.getStateId());
            } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
                containerState(slot.getContainerId(), slot.getStateId());
            } else if (packet instanceof ClientboundContainerClosePacket) {
                containerClosed();
            } else if (packet instanceof ClientboundSystemChatPacket chat && !chat.isOverlay()) {
                record(ChatText.legacy(chat.getContent()));
            } else if (packet instanceof ClientboundPlayerChatPacket chat) {
                Component content = chat.getUnsignedContent() != null ? chat.getUnsignedContent() : Component.text(chat.getContent());
                record("<" + ChatText.legacy(chat.getName()) + "> " + ChatText.legacy(content));
            } else if (packet instanceof ClientboundDisguisedChatPacket chat) {
                record("<" + ChatText.legacy(chat.getName()) + "> " + ChatText.legacy(chat.getMessage()));
            }
        }

        @Override
        public void disconnected(DisconnectedEvent event) {
            String reason = ChatText.legacy(event.getReason());
            if (event.getCause() != null) {
                joined.completeExceptionally(new IllegalStateException(reason, event.getCause()));
            } else {
                // The server closed the connection on purpose (a ban, the whitelist, a plugin).
                joined.completeExceptionally(new Rejected(name, reason));
                if (!quitting) {
                    kickReason = reason;
                }
            }
            List<Runnable> listeners;
            synchronized (Bot.this) {
                listeners = new ArrayList<>(disconnectListeners);
            }
            listeners.forEach(Runnable::run);
        }
    }

    @Override
    public String toString() {
        return "Bot[" + name + "]";
    }

    /** The server refused the login and sent this reason. */
    static final class Rejected extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        private final String reason;

        Rejected(String name, String reason) {
            super(name + " could not join: disconnected: " + reason);
            this.reason = reason;
        }

        String reason() {
            return reason;
        }
    }
}
