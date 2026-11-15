package io.github.larslewerenz.spigottesting.internal;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import org.bukkit.Bukkit;
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
            Thread thread = new Thread(() -> runTestsAndStop(new File(outputDir)), "spigot-testing");
            // JUnit loads test classes and services through the context class loader.
            thread.setContextClassLoader(classLoader);
            thread.setDaemon(true);
            thread.start();
        });
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
