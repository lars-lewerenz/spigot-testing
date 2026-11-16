package io.github.larslewerenz.spigottesting.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Client for the bot controller process that the Gradle task starts next to the server. */
public final class BotControl {

    static final String PORT_PROPERTY = "spigottesting.bots.port";
    /** The controller starts in parallel with the server, so give it a moment on first use. */
    private static final long CONNECT_TIMEOUT_MILLIS = 20_000;

    private BotControl() {
    }

    public static void join(String name) {
        request("JOIN " + name);
    }

    /** Tries to join; returns the server's reason if it refused, or null if the bot got in (and left again). */
    public static String joinExpectingRejection(String name) {
        String result = requestValue("REJECT " + name);
        return result.startsWith("REJECTED ") ? decode(result.substring("REJECTED ".length())) : null;
    }

    /** Why the server disconnected the bot, or null if it hasn't. */
    public static String kickReason(String name) {
        String reason = requestValue("KICKED " + name);
        return reason.isEmpty() ? null : decode(reason);
    }

    public static void chat(String name, String text) {
        request("CHAT " + name + " " + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
    }

    public static void quit(String name) {
        request("QUIT " + name);
    }

    /** The title of the container open on the client, or null. */
    public static String openInventoryTitle(String name) {
        String title = requestValue("INVENTORY " + name);
        return title.isEmpty() ? null : decode(title);
    }

    public static void click(String name, int slot, String type) {
        request("CLICK " + name + " " + slot + " " + type);
    }

    public static void move(String name, double x, double y, double z) {
        request("MOVE " + name + " " + x + " " + y + " " + z);
    }

    public static void breakBlock(String name, int x, int y, int z, boolean creative) {
        request("BREAK " + name + " " + x + " " + y + " " + z + " " + (creative ? "CREATIVE" : "SURVIVAL"));
    }

    public static void useItemOn(String name, int x, int y, int z, String face) {
        request("USE " + name + " " + x + " " + y + " " + z + " " + face);
    }

    /** Where the client is (x, y, z), and how many teleports it has confirmed. */
    public static double[] position(String name) {
        String[] parts = requestValue("POSITION " + name).split(" ", -1);
        return new double[] {Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
            Double.parseDouble(parts[3])};
    }

    public static void selectHotbarSlot(String name, int slot) {
        request("HOTBAR " + name + " " + slot);
    }

    public static void attack(String name, int entityId) {
        request("ATTACK " + name + " " + entityId);
    }

    public static void interact(String name, int entityId) {
        request("INTERACT " + name + " " + entityId);
    }

    public static void useItem(String name) {
        request("USE_ITEM " + name);
    }

    public static void dropItem(String name, boolean wholeStack) {
        request("DROP " + name + " " + (wholeStack ? "STACK" : "ONE"));
    }

    public static void swapHands(String name) {
        request("SWAP " + name);
    }

    public static void closeInventory(String name) {
        request("CLOSE " + name);
    }

    public static List<String> messages(String name) {
        List<String> messages = new ArrayList<>();
        for (String line : requestLines("MESSAGES " + name)) {
            messages.add(decode(line));
        }
        return messages;
    }

    /** The client view as the controller describes it: lines of {@code key [number] base64(text)}. */
    public static List<String> view(String name) {
        return requestLines("VIEW " + name);
    }

    /** Sends a request answered with "OK n" and n more lines, and returns those lines. */
    private static List<String> requestLines(String line) {
        try (Socket socket = connect()) {
            BufferedReader in = send(socket, line);
            int count = Integer.parseInt(ok(in.readLine()));
            List<String> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(in.readLine());
            }
            return lines;
        } catch (IOException e) {
            throw new IllegalStateException("Talking to the bot controller failed", e);
        }
    }

    private static void request(String line) {
        requestValue(line);
    }

    /** Sends one request and returns what follows the "OK". */
    private static String requestValue(String line) {
        try (Socket socket = connect()) {
            return ok(send(socket, line).readLine());
        } catch (IOException e) {
            throw new IllegalStateException("Talking to the bot controller failed", e);
        }
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private static BufferedReader send(Socket socket, String line) throws IOException {
        Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        out.write(line + "\n");
        out.flush();
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private static String ok(String response) {
        if (response == null) {
            throw new IllegalStateException("The bot controller closed the connection");
        }
        if (response.startsWith("ERR ")) {
            throw new IllegalStateException(response.substring(4));
        }
        return response.length() > 3 ? response.substring(3) : "";
    }

    private static Socket connect() throws IOException {
        String port = System.getProperty(PORT_PROPERTY);
        if (port == null) {
            throw new IllegalStateException("Bots are not available: -D" + PORT_PROPERTY + " is not set");
        }
        long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MILLIS;
        while (true) {
            try {
                Socket socket = new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(port));
                socket.setSoTimeout(120_000);
                return socket;
            } catch (ConnectException e) {
                if (System.currentTimeMillis() > deadline) {
                    throw e;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", interrupted);
                }
            }
        }
    }
}
