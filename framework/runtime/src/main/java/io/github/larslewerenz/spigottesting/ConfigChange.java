package io.github.larslewerenz.spigottesting;

import io.github.larslewerenz.spigottesting.internal.Cleanups;
import io.github.larslewerenz.spigottesting.internal.MainThread;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * A config value changed by {@link ServerTest#setConfig(Plugin, String, Object)}. Closing it puts
 * the old value back; that happens automatically at the end of the test.
 */
public final class ConfigChange implements AutoCloseable {

    private final Plugin plugin;
    private final String path;
    private final boolean wasSet;
    private final Object oldValue;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ConfigChange(Plugin plugin, String path, boolean wasSet, Object oldValue) {
        this.plugin = plugin;
        this.path = path;
        this.wasSet = wasSet;
        this.oldValue = oldValue;
    }

    static ConfigChange set(Plugin plugin, String path, Object value) {
        ConfigChange change = MainThread.call(() -> {
            FileConfiguration config = plugin.getConfig();
            ConfigChange c = new ConfigChange(plugin, path, config.isSet(path), config.get(path));
            config.set(path, value);
            return c;
        });
        Cleanups.register(change);
        return change;
    }

    /** Puts the old value back (or removes the key, if it wasn't set). */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            Cleanups.unregister(this);
            MainThread.call(() -> {
                plugin.getConfig().set(path, wasSet ? oldValue : null);
                return null;
            });
        }
    }

}
