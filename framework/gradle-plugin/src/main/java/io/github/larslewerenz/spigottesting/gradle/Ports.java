package io.github.larslewerenz.spigottesting.gradle;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

/**
 * Hands out free ports for servers, bot controllers and ViaProxy. A port is only free while
 * nobody listens on it, and the operating system hands out the same ports again (often in
 * sequence), so a port picked for one server run could go to the next run before the first one
 * binds it: ViaProxy binds only when the first bot joins. Ports handed out here stay reserved
 * until {@link #release} is called, so parallel server runs in this build never share one.
 */
final class Ports {

    private static final Set<Integer> RESERVED = new HashSet<>();

    private Ports() {
    }

    static synchronized int reserve() throws IOException {
        while (true) {
            int port;
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            if (RESERVED.add(port)) {
                return port;
            }
        }
    }

    static synchronized void release(int... ports) {
        for (int port : ports) {
            RESERVED.remove(port);
        }
    }
}
