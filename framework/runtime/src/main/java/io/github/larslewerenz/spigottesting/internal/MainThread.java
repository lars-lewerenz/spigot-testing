package io.github.larslewerenz.spigottesting.internal;

import java.util.concurrent.ExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs code on the server main thread from the test thread. */
public final class MainThread {

    /** Like {@link java.util.concurrent.Callable}, but may throw anything (e.g. AssertionError). */
    public interface Task<T> {
        T call() throws Throwable;
    }

    private MainThread() {
    }

    public static Plugin plugin() {
        return JavaPlugin.getPlugin(HarnessPlugin.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> T call(Task<T> task) {
        if (Bukkit.isPrimaryThread()) {
            return run(task);
        }
        // The scheduler only catches Exceptions, so an AssertionError thrown inside its task
        // would leave the future incomplete forever. Catch everything and hand it over instead.
        Object[] result = new Object[1];
        Throwable[] failure = new Throwable[1];
        try {
            Bukkit.getScheduler().callSyncMethod(plugin(), () -> {
                try {
                    result[0] = task.call();
                } catch (Throwable t) {
                    failure[0] = t;
                }
                return null;
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the main thread", e);
        } catch (ExecutionException e) {
            throw sneakyThrow(e.getCause());
        }
        if (failure[0] != null) {
            throw sneakyThrow(failure[0]);
        }
        return (T) result[0];
    }

    private static <T> T run(Task<T> task) {
        try {
            return task.call();
        } catch (Throwable t) {
            throw sneakyThrow(t);
        }
    }

    @SuppressWarnings("unchecked")
    static <E extends Throwable> RuntimeException sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }
}
