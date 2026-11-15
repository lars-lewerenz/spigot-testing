package io.github.larslewerenz.spigottesting.gradle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;

/**
 * Starts a Spigot server with the plugin under test and the test harness, waits until the harness
 * has run the tests and stopped the server, and reports the results. The server runs in a Gradle
 * worker, so the servers of different versions run in parallel.
 */
public abstract class ServerTestTask extends DefaultTask {

    @Input
    public abstract Property<String> getMinecraftVersion();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getServerJar();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getPluginJar();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getHarnessJar();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    @Input
    public abstract Property<Boolean> getAcceptEula();

    @Input
    public abstract ListProperty<String> getJvmArgs();

    @Internal
    public abstract Property<Duration> getRunTimeout();

    @Internal
    public abstract DirectoryProperty getServerDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getResultsDirectory();

    @Inject
    protected abstract WorkerExecutor getWorkers();

    @TaskAction
    public void run() {
        if (!getAcceptEula().get()) {
            throw new GradleException("Running a Minecraft server requires accepting the Minecraft EULA "
                    + "(https://aka.ms/MinecraftEULA). If you accept it, set the Gradle property "
                    + "spigotTesting.acceptEula=true (e.g. -PspigotTesting.acceptEula=true or in ~/.gradle/gradle.properties).");
        }
        JavaLauncher launcher = getJavaLauncher().get();
        getWorkers().noIsolation().submit(Work.class, p -> {
            p.getMinecraftVersion().set(getMinecraftVersion());
            p.getJavaExecutable().set(launcher.getExecutablePath());
            p.getJavaVersion().set(launcher.getMetadata().getLanguageVersion().asInt());
            p.getServerJar().set(getServerJar());
            p.getPluginJar().set(getPluginJar());
            p.getHarnessJar().set(getHarnessJar());
            p.getJvmArgs().set(getJvmArgs());
            p.getRunTimeout().set(getRunTimeout());
            p.getServerDirectory().set(getServerDirectory());
            p.getResultsDirectory().set(getResultsDirectory());
        });
    }

    public interface Params extends WorkParameters {
        Property<String> getMinecraftVersion();

        RegularFileProperty getJavaExecutable();

        Property<Integer> getJavaVersion();

        RegularFileProperty getServerJar();

        RegularFileProperty getPluginJar();

        RegularFileProperty getHarnessJar();

        ListProperty<String> getJvmArgs();

        Property<Duration> getRunTimeout();

        DirectoryProperty getServerDirectory();

        DirectoryProperty getResultsDirectory();
    }

    public abstract static class Work implements WorkAction<Params> {

        private static final Logger LOGGER = Logging.getLogger(ServerTestTask.class);
        private static final String MARKER = "##spigot-testing## ";
        /** Files the server unpacks on first start (1.18+); kept between runs to save time. */
        private static final Set<String> KEPT_FILES = Set.of("bundler", "libraries", "cache", "versions");

        @Inject
        protected abstract FileSystemOperations getFiles();

        @Override
        public void execute() {
            try {
                run();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GradleException("Interrupted", e);
            }
        }

        private void run() throws IOException, InterruptedException {
            Params p = getParameters();
            String version = p.getMinecraftVersion().get();
            File serverDir = p.getServerDirectory().get().getAsFile();
            File resultsDir = p.getResultsDirectory().get().getAsFile();
            prepareServerDirectory(serverDir.toPath());
            getFiles().delete(spec -> spec.delete(resultsDir));
            resultsDir.mkdirs();
            File log = new File(resultsDir, "server.log");

            List<String> command = new ArrayList<>();
            command.add(p.getJavaExecutable().get().getAsFile().getAbsolutePath());
            command.addAll(p.getJvmArgs().get());
            command.add("-Djava.awt.headless=true");
            command.add("-DIReallyKnowWhatIAmDoingISwear=true"); // skips the 20 s "outdated build" delay
            command.add("-Dspigottesting.outputDir=" + resultsDir.getAbsolutePath());
            command.add("-jar");
            command.add(p.getServerJar().get().getAsFile().getAbsolutePath());
            command.add("nogui");

            LOGGER.lifecycle("[{}] Starting Spigot {} on Java {}", version, version, p.getJavaVersion().get());
            long start = System.nanoTime();
            Process process = new ProcessBuilder(command)
                    .directory(serverDir)
                    .redirectErrorStream(true)
                    .start();
            Thread pump = new Thread(() -> pumpOutput(version, process.getInputStream(), log), "spigot-testing-output-" + version);
            pump.start();

            Duration timeout = p.getRunTimeout().get();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroy();
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
                pump.join(5000);
                throw new GradleException("Spigot " + version + " did not finish within " + timeout
                        + ". Server log: " + log + "\n" + Logs.tail(log, 40));
            }
            pump.join(5000);
            report(version, resultsDir, log, Duration.ofNanos(System.nanoTime() - start));
        }

        private void prepareServerDirectory(Path dir) throws IOException {
            Files.createDirectories(dir);
            try (var entries = Files.list(dir)) {
                for (Path entry : (Iterable<Path>) entries::iterator) {
                    if (!KEPT_FILES.contains(entry.getFileName().toString())) {
                        getFiles().delete(spec -> spec.delete(entry.toFile()));
                    }
                }
            }
            int port;
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }
            Files.writeString(dir.resolve("eula.txt"), "eula=true\n");
            Files.writeString(dir.resolve("server.properties"), String.join("\n",
                    "online-mode=false",
                    "server-ip=127.0.0.1",
                    "server-port=" + port,
                    "level-type=flat",
                    "generate-structures=false",
                    "allow-nether=false",
                    "spawn-monsters=false",
                    "spawn-animals=false",
                    "spawn-npcs=false",
                    "spawn-protection=0",
                    "view-distance=4",
                    "simulation-distance=4",
                    "max-players=1",
                    "enable-query=false",
                    "enable-rcon=false",
                    "snooper-enabled=false",
                    "motd=spigot-testing",
                    ""));
            Files.writeString(dir.resolve("bukkit.yml"), String.join("\n",
                    "settings:",
                    "  allow-end: false",
                    "  connection-throttle: -1",
                    ""));
            Path plugins = Files.createDirectories(dir.resolve("plugins"));
            Files.createDirectories(plugins.resolve("bStats"));
            Files.writeString(plugins.resolve("bStats/config.yml"), "enabled: false\n");
            Params p = getParameters();
            Files.copy(p.getPluginJar().get().getAsFile().toPath(), plugins.resolve("plugin-under-test.jar"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(p.getHarnessJar().get().getAsFile().toPath(), plugins.resolve("spigot-testing-harness.jar"), StandardCopyOption.REPLACE_EXISTING);
        }

        /** Writes the server output to the log file and shows the harness' test lines in the build output. */
        private static void pumpOutput(String version, InputStream output, File log) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(output, StandardCharsets.UTF_8));
                    BufferedWriter writer = Files.newBufferedWriter(log.toPath(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                    int marker = line.indexOf(MARKER);
                    if (marker >= 0) {
                        String message = line.substring(marker + MARKER.length());
                        LOGGER.lifecycle("[{}]   {}", version, message.startsWith("INFO ") ? message.substring(5) : message);
                    } else {
                        LOGGER.info("[{}] {}", version, line);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Reading the server output failed", e);
            }
        }

        private static void report(String version, File resultsDir, File log, Duration took) throws IOException {
            File summaryFile = new File(resultsDir, "summary.properties");
            if (!summaryFile.isFile()) {
                File error = new File(resultsDir, "error.txt");
                throw new GradleException("Spigot " + version + " stopped without test results"
                        + (error.isFile() ? ":\n" + Files.readString(error.toPath()) : " (did the plugin under test fail to enable?)")
                        + "\nServer log: " + log + "\n" + Logs.tail(log, 40));
            }
            Properties summary = new Properties();
            try (var in = Files.newInputStream(summaryFile.toPath())) {
                summary.load(in);
            }
            long found = Long.parseLong(summary.getProperty("found"));
            long failed = Long.parseLong(summary.getProperty("failed"));
            LOGGER.lifecycle("[{}] {}: {} tests, {} passed, {} failed, {} skipped, {} aborted ({} s)",
                    version, summary.getProperty("server"), found, summary.getProperty("succeeded"), failed,
                    summary.getProperty("skipped"), summary.getProperty("aborted"), took.toSeconds());
            if (failed > 0) {
                throw new GradleException(failed + " server test(s) failed on Spigot " + version + ":\n\n"
                        + Files.readString(new File(resultsDir, "failures.txt").toPath())
                        + "JUnit XML report: " + new File(resultsDir, "junit-xml") + "\nServer log: " + log);
            }
            if (found == 0) {
                throw new GradleException("No server tests were found. Put them in src/serverTest/java.");
            }
        }
    }
}
