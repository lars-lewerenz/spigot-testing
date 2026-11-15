package io.github.larslewerenz.spigottesting.bots;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
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

    private final String name;
    private final ClientSession session;
    private final List<String> messages = new ArrayList<>();
    private final CompletableFuture<Void> joined = new CompletableFuture<>();
    private final List<Runnable> disconnectListeners = new ArrayList<>();
    private boolean loaded;
    /** The container the server has opened for this client, or -1. */
    private int containerId = -1;
    private String containerTitle;
    /** The server's last state id; clicks must send it back. */
    private int stateId;

    private Bot(String name, ClientSession session) {
        this.name = name;
        this.session = session;
    }

    /**
     * Connects and returns once the player has spawned (its spawn position is confirmed); some
     * versions ignore commands from a player before that. A join that stalls is
     * retried: through ViaProxy, a quick reconnect with the same name occasionally hangs in the
     * configuration phase although the server has already logged the player in.
     */
    static Bot connect(String name, String host, int port) throws Exception {
        for (int attempt = 1; ; attempt++) {
            ClientSession session = ClientNetworkSessionFactory.factory()
                    .setAddress(host, port)
                    .setProtocol(new MinecraftProtocol(name))
                    .create();
            Bot bot = new Bot(name, session);
            session.addListener(bot.new Listener());
            session.connect(true);
            try {
                bot.joined.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return bot;
            } catch (ExecutionException e) {
                throw new IllegalStateException(name + " could not join: " + BotController.oneLine(e.getCause()), e.getCause());
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

    synchronized List<String> messages() {
        return new ArrayList<>(messages);
    }

    void quit() {
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
            if (packet instanceof ClientboundPlayerPositionPacket position) {
                // Confirm the server-side teleports, like a real client.
                session.send(new ServerboundAcceptTeleportationPacket(position.getId(),
                        position.getPosition().getX(), position.getPosition().getY(), position.getPosition().getZ(),
                        position.getYRot(), position.getXRot()));
                if (!loaded) {
                    loaded = true;
                    session.send(ServerboundPlayerLoadedPacket.INSTANCE);
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
            joined.completeExceptionally(event.getCause() != null
                    ? new IllegalStateException(reason, event.getCause())
                    : new IllegalStateException("disconnected: " + reason));
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
}
