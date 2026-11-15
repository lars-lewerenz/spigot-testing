package io.github.larslewerenz.spigottesting.internal;

import io.github.larslewerenz.spigottesting.Bots;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Disconnects bots a test left online, so every test starts without players. Runs on the test
 * thread (callbacks are not moved to the main thread), where waiting for the server is allowed.
 */
public final class BotCleanup implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        Bots.quitAll();
    }
}
