package io.github.larslewerenz.spigottesting;

import io.github.larslewerenz.spigottesting.internal.Cleanups;
import io.github.larslewerenz.spigottesting.internal.MainThread;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/**
 * Records events of one type, as they look after every plugin has handled them (MONITOR
 * priority, cancelled ones included). Created with {@link ServerTest#recordEvents(Class)} and
 * removed automatically at the end of the test, or earlier with {@link #close()}.
 */
public final class EventRecorder<T extends Event> implements Listener, AutoCloseable {

    private static final long TIMEOUT_MILLIS = 10_000;

    private final Class<T> type;
    private final List<T> events = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private EventRecorder(Class<T> type) {
        this.type = type;
    }

    static <T extends Event> EventRecorder<T> start(Class<T> type) {
        EventRecorder<T> recorder = new EventRecorder<>(type);
        // Events of subclasses that share the handler list arrive here too, so filter by type.
        MainThread.call(() -> {
            Bukkit.getPluginManager().registerEvent(type, recorder, EventPriority.MONITOR,
                    (listener, event) -> {
                        if (type.isInstance(event)) {
                            recorder.events.add(type.cast(event));
                        }
                    },
                    MainThread.plugin(), false);
            return null;
        });
        Cleanups.register(recorder);
        return recorder;
    }

    /** The events recorded so far, oldest first. */
    public List<T> events() {
        return new ArrayList<>(events);
    }

    /**
     * Waits up to 10 seconds for an event matching {@code condition} (including ones recorded
     * before the call) and returns it. Only callable from tests annotated with {@link OffMainThread}.
     */
    public T awaitEvent(Predicate<? super T> condition) {
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("awaitEvent() needs the server to tick; annotate the test with @OffMainThread");
        }
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        do {
            for (T event : events) {
                if (condition.test(event)) {
                    return event;
                }
            }
            ServerTest.awaitTicks(1);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("No matching " + type.getSimpleName() + " within " + TIMEOUT_MILLIS / 1000
                + " s. Recorded: " + events);
    }

    /** Waits up to 10 seconds for the first event and returns it. */
    public T awaitEvent() {
        return awaitEvent(event -> true);
    }

    /** Stops recording. Happens automatically at the end of the test. */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            Cleanups.unregister(this);
            MainThread.call(() -> {
                HandlerList.unregisterAll(this);
                return null;
            });
        }
    }
}
