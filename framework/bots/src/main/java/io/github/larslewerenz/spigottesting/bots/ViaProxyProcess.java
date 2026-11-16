package io.github.larslewerenz.spigottesting.bots;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * ViaProxy (https://github.com/ViaVersion/ViaProxy) translates between the bots' protocol version
 * and the server's. It runs as its own process and is only started when the first bot joins.
 * The Gradle plugin downloads the jar before the test servers start.
 */
final class ViaProxyProcess {

    private static final long START_TIMEOUT_MILLIS = 60_000;
    /** ViaProxy binds to 127.0.0.1 (see --bind-address), so connect to exactly that, not to "localhost". */
    private static final InetAddress LOOPBACK = ipv4Loopback();

    private final int port;
    private final int serverPort;
    private final Path jar;
    private final Path workDir;
    private Process process;

    ViaProxyProcess(int port, int serverPort, Path jar, Path workDir) {
        this.port = port;
        this.serverPort = serverPort;
        this.jar = jar;
        this.workDir = workDir;
    }

    int port() {
        return port;
    }

    synchronized void ensureStarted() throws IOException, InterruptedException {
        if (process != null && process.isAlive()) {
            return;
        }
        Path dir = Files.createDirectories(workDir.resolve("viaproxy"));
        String java = ProcessHandle.current().info().command().orElse("java");
        process = new ProcessBuilder(List.of(java, "-jar", jar.toAbsolutePath().toString(), "cli",
                "--bind-address", "127.0.0.1:" + port,
                "--target-address", "127.0.0.1:" + serverPort,
                "--auth-method", "NONE",
                "--chat-signing", "false",
                "--log-ips", "false"))
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(workDir.resolve("viaproxy.log").toFile())
                .start();
        long deadline = System.currentTimeMillis() + START_TIMEOUT_MILLIS;
        while (!isListening()) {
            if (!process.isAlive()) {
                throw new IllegalStateException("ViaProxy exited with code " + process.exitValue() + ", see " + workDir.resolve("viaproxy.log"));
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("ViaProxy did not start within " + START_TIMEOUT_MILLIS / 1000 + " s");
            }
            Thread.sleep(100);
        }
    }

    private boolean isListening() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(LOOPBACK, port), 200);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static InetAddress ipv4Loopback() {
        try {
            return InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        } catch (java.net.UnknownHostException e) {
            throw new IllegalStateException(e); // can't happen for a 4-byte address
        }
    }

    synchronized void stop() {
        if (process != null) {
            process.destroy();
        }
    }
}
