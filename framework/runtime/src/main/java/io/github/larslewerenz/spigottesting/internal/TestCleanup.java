package io.github.larslewerenz.spigottesting.internal;

import io.github.larslewerenz.spigottesting.Bots;
import io.github.larslewerenz.spigottesting.EventRecorder;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Undoes what a test left behind, so every test starts from the same state: stops event
 * recorders and disconnects bots. Runs on the test thread (callbacks are not moved to the main
 * thread), where waiting for the server is allowed.
 */
public final class TestCleanup implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        EventRecorder.closeAll();
        Bots.quitAll();
    }
}
