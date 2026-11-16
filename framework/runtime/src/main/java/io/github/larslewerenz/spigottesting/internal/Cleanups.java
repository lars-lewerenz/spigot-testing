package io.github.larslewerenz.spigottesting.internal;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Things a test opened that must be undone when it ends (event recorders, config changes).
 * They register here when created; {@link TestCleanup} closes what is still open after each test.
 */
public final class Cleanups {

    private static final Deque<AutoCloseable> OPEN = new ConcurrentLinkedDeque<>();

    private Cleanups() {
    }

    public static void register(AutoCloseable resource) {
        OPEN.push(resource);
    }

    public static void unregister(AutoCloseable resource) {
        OPEN.remove(resource);
    }

    /** Closes everything still open, newest first, and rethrows the first failure. */
    static void closeAll() throws Exception {
        Exception failure = null;
        AutoCloseable resource;
        while ((resource = OPEN.poll()) != null) {
            try {
                resource.close();
            } catch (Exception e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
