package io.github.larslewerenz.spigottesting;

import io.github.larslewerenz.spigottesting.internal.MainThread;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * A config value changed by {@link ServerTest#setConfig(Plugin, String, Object)}. Closing it puts
 * the old value back; that happens automatically at the end of the test.
 */
public final class ConfigChange implements AutoCloseable {

    private static final List<ConfigChange> OPEN = new CopyOnWriteArrayList<>();

    private final Plugin plugin;
    private final String path;
    private final boolean wasSet;
    private final Object oldValue;

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
        OPEN.add(change);
        return change;
    }

    /** Puts the old value back (or removes the key, if it wasn't set). */
    @Override
    public void close() {
        if (OPEN.remove(this)) {
            MainThread.call(() -> {
                plugin.getConfig().set(path, wasSet ? oldValue : null);
                return null;
            });
        }
    }

    /** Called after each test; restores the newest changes first. */
    public static void restoreAll() {
        for (int i = OPEN.size() - 1; i >= 0; i--) {
            OPEN.get(i).close();
        }
    }
}
