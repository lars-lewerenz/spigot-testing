package io.github.larslewerenz.spigottesting.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The plugin the Gradle plugin builds around the test classes. It depends on the plugin under
 * test, starts the tests on the first server tick (after every plugin is enabled and the worlds
 * are loaded) and stops the server when they are done.
 */
public final class HarnessPlugin extends JavaPlugin {

    static final String OUTPUT_DIR_PROPERTY = "spigottesting.outputDir";

    @Override
    public void onEnable() {
        String outputDir = System.getProperty(OUTPUT_DIR_PROPERTY);
        if (outputDir == null) {
            getLogger().warning("-D" + OUTPUT_DIR_PROPERTY + " is not set, not running any tests");
            return;
        }
        ClassLoader classLoader = getClassLoader();
        getServer().getScheduler().runTask(this, () -> {
            MonsterSpawning.applyProperty(getServer());
            String missing = missingDependency();
            if (missing != null) {
                // Modern Paper loads this harness even when the plugin under test failed to load,
                // and a plugin can fail in onEnable and be disabled: don't run the tests without it.
                getLogger().severe(missing);
                writeError(new File(outputDir), new IllegalStateException(missing));
                Bukkit.shutdown();
                return;
            }
            Thread thread = new Thread(() -> runTestsAndStop(new File(outputDir)), "spigot-testing");
            // JUnit loads test classes and services through the context class loader.
            thread.setContextClassLoader(classLoader);
            thread.setDaemon(true);
            thread.start();
        });
    }

    /** A message if the plugin under test isn't loaded and enabled, else null. */
    private String missingDependency() {
        String name = pluginUnderTest();
        if (name == null) {
            return null;
        }
        Plugin plugin = getServer().getPluginManager().getPlugin(name);
        if (plugin == null) {
            return "The plugin under test (" + name + ") was not loaded; see the errors in the server log";
        }
        if (!plugin.isEnabled()) {
            return "The plugin under test (" + name + ") is not enabled: it failed while enabling; see the server log";
        }
        return null;
    }

    /** Written by the Gradle plugin; the same for plugin.yml and paper-plugin.yml plugins. */
    private String pluginUnderTest() {
        try (InputStream in = getClassLoader().getResourceAsStream("spigot-testing/plugin-under-test.txt")) {
            if (in == null) {
                return null;
            }
            byte[] bytes = new byte[4096];
            int length = 0;
            for (int read; (read = in.read(bytes, length, bytes.length - length)) > 0; ) {
                length += read;
            }
            return new String(bytes, 0, length, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private void runTestsAndStop(File outputDir) {
        try {
            new TestRunner(this, outputDir).run();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Running the tests failed", t);
            writeError(outputDir, t);
        } finally {
            Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
        }
    }

    private void writeError(File outputDir, Throwable t) {
        StringWriter trace = new StringWriter();
        t.printStackTrace(new PrintWriter(trace));
        try {
            Files.createDirectories(outputDir.toPath());
            Files.write(new File(outputDir, "error.txt").toPath(), trace.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not write error.txt", e);
        }
    }
}
