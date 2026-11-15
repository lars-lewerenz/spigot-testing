package io.github.larslewerenz.spigottesting.bots;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
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
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final String name;
    private final ClientSession session;
    private final List<String> messages = new ArrayList<>();
    private final CompletableFuture<Void> joined = new CompletableFuture<>();
    private final List<Runnable> disconnectListeners = new ArrayList<>();
    private boolean loaded;

    private Bot(String name, ClientSession session) {
        this.name = name;
        this.session = session;
    }

    /**
     * Connects and returns once the server has sent the join (login) packet. A join that stalls is
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
            if (packet instanceof ClientboundLoginPacket) {
                joined.complete(null);
            } else if (packet instanceof ClientboundPlayerPositionPacket position) {
                // Confirm the server-side teleports, like a real client.
                session.send(new ServerboundAcceptTeleportationPacket(position.getId(),
                        position.getPosition().getX(), position.getPosition().getY(), position.getPosition().getZ(),
                        position.getYRot(), position.getXRot()));
                if (!loaded) {
                    loaded = true;
                    session.send(ServerboundPlayerLoadedPacket.INSTANCE);
                }
            } else if (packet instanceof ClientboundSystemChatPacket chat && !chat.isOverlay()) {
                record(LEGACY.serialize(chat.getContent()));
            } else if (packet instanceof ClientboundPlayerChatPacket chat) {
                Component content = chat.getUnsignedContent() != null ? chat.getUnsignedContent() : Component.text(chat.getContent());
                record("<" + LEGACY.serialize(chat.getName()) + "> " + LEGACY.serialize(content));
            } else if (packet instanceof ClientboundDisguisedChatPacket chat) {
                record("<" + LEGACY.serialize(chat.getName()) + "> " + LEGACY.serialize(chat.getMessage()));
            }
        }

        @Override
        public void disconnected(DisconnectedEvent event) {
            String reason = LEGACY.serialize(event.getReason());
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
