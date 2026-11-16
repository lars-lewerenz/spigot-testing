package io.github.larslewerenz.spigottesting.internal;

/** Lines for the build output, next to the test results (the Gradle task shows marked lines). */
public final class TestLog {

    private TestLog() {
    }

    /** Shows {@code message} as a warning in the build output. */
    public static void warning(String message) {
        MainThread.plugin().getLogger().warning(ProgressListener.MARKER + "WARNING " + message);
    }
}
