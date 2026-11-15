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

    public static void chat(String name, String text) {
        request("CHAT " + name + " " + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
    }

    public static void quit(String name) {
        request("QUIT " + name);
    }

    public static List<String> messages(String name) {
        try (Socket socket = connect()) {
            BufferedReader in = send(socket, "MESSAGES " + name);
            int count = Integer.parseInt(ok(in.readLine()));
            List<String> messages = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                messages.add(new String(Base64.getDecoder().decode(in.readLine()), StandardCharsets.UTF_8));
            }
            return messages;
        } catch (IOException e) {
            throw new IllegalStateException("Talking to the bot controller failed", e);
        }
    }

    private static void request(String line) {
        try (Socket socket = connect()) {
            ok(send(socket, line).readLine());
        } catch (IOException e) {
            throw new IllegalStateException("Talking to the bot controller failed", e);
        }
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
