package io.github.larslewerenz.spigottesting.bots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Runs next to a test server and connects bot clients to it on request. The in-server test
 * runtime talks to it over a small line protocol on localhost, one request per connection:
 *
 * <pre>
 * JOIN name [version]    -> OK | ERR reason    (version: the client version to join as; default: the server's)
 * REJECT name            -> OK REJECTED base64(reason) | OK JOINED   (the bot quits again right away)
 * KICKED name            -> OK base64(reason) | OK  (still online, or never kicked)
 * CHAT name base64(text) -> OK | ERR reason    (text starting with "/" is sent as a command)
 * MESSAGES name          -> OK n, then n lines of base64(message)
 * VIEW name              -> OK n, then n lines "key [number] base64(text)" (see ClientView#describe)
 * QUIT name              -> OK
 * INVENTORY name         -> OK base64(title of the open container) | OK  (nothing open)
 * CLICK name slot type   -> OK | ERR reason    (type: LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT)
 * CLOSE name             -> OK
 * MOVE name x y z        -> OK | ERR reason    (walks there; answers once the last step is sent)
 * BREAK name x y z mode  -> OK                 (mode: CREATIVE or SURVIVAL)
 * USE name x y z face    -> OK                 (face: UP, DOWN, NORTH, SOUTH, EAST, WEST)
 * HOTBAR name slot       -> OK
 * ATTACK name entityId   -> OK                 (hits the entity)
 * INTERACT name entityId -> OK                 (right-clicks the entity)
 * USE_ITEM name          -> OK                 (right-clicks with the item in hand, not at a block)
 * DROP name ONE|STACK    -> OK                 (Q / Ctrl+Q)
 * SWAP name              -> OK                 (swaps main and off hand)
 * POSITION name          -> OK x y z n         (where the client is, and how many teleports it confirmed)
 * </pre>
 *
 * <p>Bots connect through ViaProxy, so the one protocol version MCProtocolLib speaks works with
 * every server version. ViaProxy is only started when the first bot joins.
 */
public final class BotController {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    private final ViaProxyProcess proxy;
    private final Map<String, Bot> bots = new ConcurrentHashMap<>();
    /** Why the server disconnected a bot, by name, until the name joins again. */
    private final Map<String, String> kicks = new ConcurrentHashMap<>();

    private BotController(ViaProxyProcess proxy) {
        this.proxy = proxy;
    }

    /** Arguments: control port, ViaProxy port, server port, ViaProxy jar, working directory. */
    public static void main(String[] args) throws IOException {
        if (args.length != 5) {
            System.err.println("Usage: BotController <control-port> <proxy-port> <server-port> <viaproxy-jar> <work-dir>");
            System.exit(2);
        }
        int controlPort = Integer.parseInt(args[0]);
        ViaProxyProcess proxy = new ViaProxyProcess(Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                Path.of(args[3]), Path.of(args[4]));
        BotController controller = new BotController(proxy);
        Runtime.getRuntime().addShutdownHook(new Thread(controller::shutdown));
        exitWhenParentIsGone();

        try (ServerSocket server = new ServerSocket(controlPort, 50, java.net.InetAddress.getLoopbackAddress())) {
            System.out.println("Bot controller listening on " + controlPort);
            while (true) {
                Socket socket = server.accept();
                Thread.ofVirtual().start(() -> controller.handle(socket));
            }
        }
    }

    /** The test task keeps our stdin open; when it closes, the task is gone and we must go too. */
    private static void exitWhenParentIsGone() {
        Thread watcher = new Thread(() -> {
            try {
                while (System.in.read() != -1) {
                    // ignore input
                }
            } catch (IOException ignored) {
                // treat like end of input
            }
            System.exit(0);
        }, "stdin-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void handle(Socket socket) {
        try (socket;
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            String request = in.readLine();
            if (request == null) {
                return;
            }
            try {
                respond(request.split(" ", 3), out);
            } catch (Exception e) {
                out.println("ERR " + oneLine(e));
            }
        } catch (IOException e) {
            System.err.println("Control connection failed: " + e);
        }
    }

    private void respond(String[] request, PrintWriter out) throws Exception {
        String command = request[0];
        String name = request.length > 1 ? request[1] : "";
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid player name '" + name + "' (1-16 letters, digits or _)");
        }
        switch (command) {
            case "JOIN" -> {
                if (bots.containsKey(name)) {
                    throw new IllegalStateException(name + " is already online");
                }
                proxy.ensureStarted();
                kicks.remove(name);
                String version = request.length > 2 ? request[2] : Bot.SERVER_VERSION;
                if (!VERSION.matcher(version).matches()) {
                    throw new IllegalArgumentException("Invalid client version '" + version + "'");
                }
                Bot bot;
                try {
                    bot = Bot.connect(name, "127.0.0.1", proxy.port(), proxy.serverPort(), version);
                } catch (IllegalStateException e) {
                    if (!version.equals(Bot.SERVER_VERSION) && oneLine(e).contains("endOfStream")) {
                        // ViaProxy closes the connection without a word for versions it doesn't know.
                        throw new IllegalStateException(oneLine(e) + " (is " + version
                                + " a Minecraft version ViaProxy knows, like 1.8.8 or 1.20.4?)", e);
                    }
                    throw e;
                }
                bots.put(name, bot);
                bot.onDisconnect(() -> {
                    if (bots.remove(name, bot) && bot.kickReason() != null) {
                        kicks.put(name, bot.kickReason());
                    }
                });
                out.println("OK");
            }
            case "REJECT" -> {
                if (bots.containsKey(name)) {
                    throw new IllegalStateException(name + " is already online");
                }
                proxy.ensureStarted();
                try {
                    Bot.connect(name, "127.0.0.1", proxy.port(), proxy.serverPort(), Bot.SERVER_VERSION).quit();
                    out.println("OK JOINED");
                } catch (Bot.Rejected rejected) {
                    out.println("OK REJECTED " + base64(rejected.reason()));
                }
            }
            case "KICKED" -> {
                String reason = kicks.get(name);
                out.println(reason == null ? "OK" : "OK " + base64(reason));
            }
            case "CHAT" -> {
                bot(name).chat(new String(Base64.getDecoder().decode(request[2]), StandardCharsets.UTF_8));
                out.println("OK");
            }
            case "MESSAGES" -> {
                List<String> messages = bot(name).messages();
                out.println("OK " + messages.size());
                for (String message : messages) {
                    out.println(base64(message));
                }
            }
            case "VIEW" -> {
                List<String> lines = bot(name).view();
                out.println("OK " + lines.size());
                lines.forEach(out::println);
            }
            case "INVENTORY" -> {
                String title = bot(name).openContainerTitle();
                out.println(title == null ? "OK" : "OK " + base64(title));
            }
            case "CLICK" -> {
                String[] args = request[2].split(" ", 2);
                bot(name).click(Integer.parseInt(args[0]), args[1]);
                out.println("OK");
            }
            case "MOVE" -> {
                String[] xyz = request[2].split(" ", -1);
                bot(name).moveTo(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]));
                out.println("OK");
            }
            case "BREAK" -> {
                String[] args = request[2].split(" ", -1);
                bot(name).breakBlock(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                        args[3].equals("CREATIVE"));
                out.println("OK");
            }
            case "USE" -> {
                String[] args = request[2].split(" ", -1);
                bot(name).useItemOn(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]), args[3]);
                out.println("OK");
            }
            case "POSITION" -> {
                double[] at = bot(name).clientPosition();
                out.println("OK " + at[0] + " " + at[1] + " " + at[2] + " " + (long) at[3]);
            }
            case "HOTBAR" -> {
                bot(name).selectHotbarSlot(Integer.parseInt(request[2]));
                out.println("OK");
            }
            case "ATTACK" -> {
                bot(name).attack(Integer.parseInt(request[2]));
                out.println("OK");
            }
            case "INTERACT" -> {
                bot(name).interact(Integer.parseInt(request[2]));
                out.println("OK");
            }
            case "USE_ITEM" -> {
                bot(name).useItem();
                out.println("OK");
            }
            case "DROP" -> {
                bot(name).dropItem(request[2].equals("STACK"));
                out.println("OK");
            }
            case "SWAP" -> {
                bot(name).swapHands();
                out.println("OK");
            }
            case "CLOSE" -> {
                bot(name).closeContainer();
                out.println("OK");
            }
            case "QUIT" -> {
                kicks.remove(name);
                Bot bot = bots.remove(name);
                if (bot != null) {
                    bot.quit();
                }
                out.println("OK");
            }
            default -> throw new IllegalArgumentException("Unknown command " + command);
        }
    }

    private Bot bot(String name) {
        Bot bot = bots.get(name);
        if (bot == null) {
            throw new IllegalStateException(name + " is not online");
        }
        return bot;
    }

    private void shutdown() {
        bots.values().forEach(Bot::quit);
        proxy.stop();
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static String oneLine(Throwable t) {
        String message = t.getMessage() == null ? t.toString() : t.getMessage();
        return message.replace('\n', ' ').replace('\r', ' ');
    }
}
