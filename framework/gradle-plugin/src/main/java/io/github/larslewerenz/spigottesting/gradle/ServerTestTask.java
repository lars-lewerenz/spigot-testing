package io.github.larslewerenz.spigottesting.gradle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
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
@DisableCachingByDefault(because = "Runs tests on a live server; results are not reusable")
public abstract class ServerTestTask extends DefaultTask {

    @Input
    public abstract Property<String> getMinecraftVersion();

    /** Shown in the build output, e.g. "Spigot 1.8.8" or "Paper 26.3". */
    @Input
    public abstract Property<String> getServerName();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getServerJar();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getPluginJar();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getHarnessJar();

    /**
     * Directories laid onto the server directory before the start, in order (later ones win):
     * src/serverTest/server, server-&lt;type&gt;, server-&lt;version&gt;, server-&lt;type&gt;-&lt;version&gt;.
     */
    @Internal
    public abstract ListProperty<org.gradle.api.file.Directory> getServerFileLayers();

    /** The files of {@link #getServerFileLayers()}, so that changing them reruns the tests. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getServerFiles();

    /** "spigot" or "paper", passed to the tests as {@code ServerTest.serverType()}. */
    @Input
    public abstract Property<String> getServerType();

    /** Other plugins installed next to the plugin under test. */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getOtherPlugins();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /** The bot controller (fake players), started next to the server. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getBotControllerJar();

    @Nested
    public abstract Property<JavaLauncher> getBotJavaLauncher();

    /** Lets the bots join every server version; started by the bot controller on first use. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getViaProxyJar();

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
            throw new GradleException(CheckEula.MESSAGE);
        }
        JavaLauncher launcher = getJavaLauncher().get();
        getWorkers().noIsolation().submit(Work.class, p -> {
            p.getMinecraftVersion().set(getMinecraftVersion());
            p.getServerName().set(getServerName());
            p.getJavaExecutable().set(launcher.getExecutablePath());
            p.getJavaVersion().set(launcher.getMetadata().getLanguageVersion().asInt());
            p.getBotControllerJar().set(getBotControllerJar());
            p.getBotJavaExecutable().set(getBotJavaLauncher().get().getExecutablePath());
            p.getViaProxyJar().set(getViaProxyJar());
            p.getServerJar().set(getServerJar());
            p.getPluginJar().set(getPluginJar());
            p.getHarnessJar().set(getHarnessJar());
            p.getOtherPlugins().from(getOtherPlugins());
            p.getServerFileLayers().set(getServerFileLayers());
            p.getServerType().set(getServerType());
            p.getJvmArgs().set(getJvmArgs());
            p.getRunTimeout().set(getRunTimeout());
            p.getServerDirectory().set(getServerDirectory());
            p.getResultsDirectory().set(getResultsDirectory());
        });
    }

    public interface Params extends WorkParameters {
        Property<String> getMinecraftVersion();

        Property<String> getServerName();

        RegularFileProperty getJavaExecutable();

        Property<Integer> getJavaVersion();

        RegularFileProperty getBotControllerJar();

        RegularFileProperty getBotJavaExecutable();

        RegularFileProperty getViaProxyJar();

        RegularFileProperty getServerJar();

        RegularFileProperty getPluginJar();

        RegularFileProperty getHarnessJar();

        ConfigurableFileCollection getOtherPlugins();

        ListProperty<org.gradle.api.file.Directory> getServerFileLayers();

        Property<String> getServerType();

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
        /** Written by the test runtime when there are @AfterRestart tests. */
        private static final String RESTART_REQUEST = "restart-requested";
        /** The phase name passed to the server, and the results subfolder of that run. */
        private static final String AFTER_RESTART = "after-restart";

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
            String version = p.getServerName().get();
            File serverDir = p.getServerDirectory().get().getAsFile();
            File resultsDir = p.getResultsDirectory().get().getAsFile();
            int serverPort = Ports.reserve();
            int botPort = Ports.reserve();
            int proxyPort = Ports.reserve();
            try {
                prepareServerDirectory(serverDir.toPath(), serverPort);
                getFiles().delete(spec -> spec.delete(resultsDir));
                resultsDir.mkdirs();
                Process botController = startBotController(botPort, proxyPort, serverPort, resultsDir);
                try {
                    long start = System.nanoTime();
                    List<File> runs = new ArrayList<>();
                    runs.add(resultsDir);
                    runServer(version, serverDir, resultsDir, botPort, null);
                    if (new File(resultsDir, RESTART_REQUEST).isFile()) {
                        // Same server folder, nothing wiped: what the plugins saved is still there.
                        File afterRestart = new File(resultsDir, AFTER_RESTART);
                        afterRestart.mkdirs();
                        LOGGER.lifecycle("[{}] Restarting the server for the @AfterRestart tests", version);
                        runServer(version, serverDir, afterRestart, botPort, AFTER_RESTART);
                        runs.add(afterRestart);
                    }
                    String pluginName = GenerateHarnessResources.readPluginName(p.getPluginJar().get().getAsFile());
                    report(version, pluginName, runs, Duration.ofNanos(System.nanoTime() - start));
                } finally {
                    botController.getOutputStream().close(); // makes the controller exit
                    if (!botController.waitFor(10, TimeUnit.SECONDS)) {
                        botController.destroyForcibly();
                    }
                }
            } finally {
                Ports.release(serverPort, botPort, proxyPort);
            }
        }

        /** Started for every run (it is small); ViaProxy is only started once a test uses bots. */
        private Process startBotController(int controlPort, int proxyPort, int serverPort, File resultsDir) throws IOException {
            Params p = getParameters();
            File workDir = new File(resultsDir, "bots");
            workDir.mkdirs();
            return new ProcessBuilder(
                    p.getBotJavaExecutable().get().getAsFile().getAbsolutePath(),
                    "-jar", p.getBotControllerJar().get().getAsFile().getAbsolutePath(),
                    String.valueOf(controlPort),
                    String.valueOf(proxyPort),
                    String.valueOf(serverPort),
                    p.getViaProxyJar().get().getAsFile().getAbsolutePath(),
                    workDir.getAbsolutePath())
                    .redirectErrorStream(true)
                    .redirectOutput(new File(resultsDir, "bot-controller.log"))
                    .start();
        }

        /** Runs the server once; {@code phase} is null for the first run. */
        private void runServer(String version, File serverDir, File resultsDir, int botPort, String phase)
                throws IOException, InterruptedException {
            Params p = getParameters();
            File log = new File(resultsDir, "server.log");
            List<String> command = new ArrayList<>();
            command.add(p.getJavaExecutable().get().getAsFile().getAbsolutePath());
            command.addAll(p.getJvmArgs().get());
            command.add("-Djava.awt.headless=true");
            command.add("-DIReallyKnowWhatIAmDoingISwear=true"); // skips the 20 s "outdated build" delay
            command.add("-Dspigottesting.outputDir=" + resultsDir.getAbsolutePath());
            command.add("-Dspigottesting.bots.port=" + botPort);
            command.add("-Dspigottesting.serverType=" + p.getServerType().get());
            command.add("-Dspigottesting.minecraftVersion=" + p.getMinecraftVersion().get());
            if (phase != null) {
                command.add("-Dspigottesting.phase=" + phase);
            }
            command.add("-jar");
            command.add(p.getServerJar().get().getAsFile().getAbsolutePath());
            command.add("nogui");

            LOGGER.lifecycle("[{}] Starting {} on Java {}", version, version, p.getJavaVersion().get());
            Process process = new ProcessBuilder(command)
                    .directory(serverDir)
                    .redirectErrorStream(true)
                    .start();
            ServerWatch watch = new ServerWatch();
            Thread pump = new Thread(() -> pumpOutput(version, process.getInputStream(), log, watch),
                    "spigot-testing-output-" + version.replace(' ', '-'));
            pump.start();

            Duration timeout = p.getRunTimeout().get();
            long deadline = System.nanoTime() + timeout.toNanos();
            boolean stopRequested = false;
            while (!process.waitFor(1, TimeUnit.SECONDS)) {
                if (System.nanoTime() > deadline) {
                    process.destroy();
                    if (!process.waitFor(15, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                    pump.join(5000);
                    throw new GradleException(version + " did not finish within " + timeout
                            + ". Server log: " + log + "\n" + Logs.problems(log, 30));
                }
                if (!stopRequested && watch.harnessMissing()) {
                    // The harness never started (usually because the plugin under test could not
                    // be loaded, so the harness that depends on it wasn't either): stop the server
                    // instead of idling until the timeout.
                    stopRequested = true;
                    LOGGER.lifecycle("[{}] The test harness did not start; stopping the server", version);
                    process.getOutputStream().write("stop\n".getBytes(StandardCharsets.UTF_8));
                    process.getOutputStream().flush();
                }
            }
            pump.join(5000);
        }

        /** Writes a fresh server configuration for a server on the given port. */
        private void prepareServerDirectory(Path dir, int port) throws IOException {
            Files.createDirectories(dir);
            try (var entries = Files.list(dir)) {
                for (Path entry : (Iterable<Path>) entries::iterator) {
                    if (!KEPT_FILES.contains(entry.getFileName().toString())) {
                        getFiles().delete(spec -> spec.delete(entry.toFile()));
                    }
                }
            }
            Files.writeString(dir.resolve("eula.txt"), "eula=true\n");
            Files.writeString(dir.resolve("bukkit.yml"), String.join("\n",
                    "settings:",
                    "  allow-end: false",
                    "  connection-throttle: -1", // bots reconnect quickly
                    ""));
            Files.createDirectories(dir.resolve("plugins/bStats"));
            Files.writeString(dir.resolve("plugins/bStats/config.yml"), "enabled: false\n");

            // The project's server files (src/serverTest/server*), then server.properties.
            Map<String, String> properties = ServerFiles.defaultProperties(port);
            List<Path> layers = getParameters().getServerFileLayers().get().stream().map(d -> d.getAsFile().toPath()).toList();
            for (String warning : ServerFiles.apply(layers, dir, properties)) {
                LOGGER.warn(warning);
            }
            Files.writeString(dir.resolve(ServerFiles.PROPERTIES), ServerFiles.toPropertiesFile(properties));

            Path plugins = Files.createDirectories(dir.resolve("plugins"));
            Files.createDirectories(plugins.resolve("bStats"));
            Files.writeString(plugins.resolve("bStats/config.yml"), "enabled: false\n");
            Params p = getParameters();
            Files.copy(p.getPluginJar().get().getAsFile().toPath(), plugins.resolve("plugin-under-test.jar"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(p.getHarnessJar().get().getAsFile().toPath(), plugins.resolve("spigot-testing-harness.jar"), StandardCopyOption.REPLACE_EXISTING);
            int index = 0;
            for (File other : p.getOtherPlugins().getFiles()) {
                Files.copy(other.toPath(), plugins.resolve("other-" + index++ + "-" + other.getName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        /** What the server output has shown so far. */
        static final class ServerWatch {
            /** How long the harness gets after the server is up to announce its test run. */
            static final long HARNESS_GRACE_NANOS = TimeUnit.SECONDS.toNanos(30);

            private volatile long startedAt;
            private volatile boolean harnessStarted;

            void line(String line) {
                // "Done (1.234s)! For help, type "help"": the startup line of every version.
                if (startedAt == 0 && line.contains("Done (") && line.contains("For help")) {
                    startedAt = System.nanoTime();
                }
                if (line.contains(MARKER)) {
                    harnessStarted = true;
                }
            }

            boolean harnessMissing() {
                return startedAt != 0 && !harnessStarted && System.nanoTime() - startedAt > HARNESS_GRACE_NANOS;
            }
        }

        /** Writes the server output to the log file and shows the harness' test lines in the build output. */
        private static void pumpOutput(String version, InputStream output, File log, ServerWatch watch) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(output, StandardCharsets.UTF_8));
                    BufferedWriter writer = Files.newBufferedWriter(log.toPath(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                    watch.line(line);
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

        /** Reports the results of the server runs (one, or two with a restart) and fails on failures. */
        private static void report(String version, String pluginName, List<File> runs, Duration took)
                throws IOException {
            long found = 0;
            long succeeded = 0;
            long failed = 0;
            long skipped = 0;
            long aborted = 0;
            String server = null;
            StringBuilder failures = new StringBuilder();
            for (File resultsDir : runs) {
                File log = new File(resultsDir, "server.log");
                String run = resultsDir.getName().equals(AFTER_RESTART) ? version + " (after the restart)" : version;
                File summaryFile = new File(resultsDir, "summary.properties");
                if (!summaryFile.isFile()) {
                    File error = new File(resultsDir, "error.txt");
                    throw new GradleException(run + " stopped without test results"
                            + (error.isFile() ? ":\n" + Files.readString(error.toPath()) : " (did the plugin under test fail to load or enable?)")
                            + "\nServer log: " + log + "\n" + Logs.problems(log, 30));
                }
                Properties summary = new Properties();
                try (var in = Files.newInputStream(summaryFile.toPath())) {
                    summary.load(in);
                }
                if (server == null) {
                    server = summary.getProperty("server");
                }
                found += Long.parseLong(summary.getProperty("found"));
                succeeded += Long.parseLong(summary.getProperty("succeeded"));
                failed += Long.parseLong(summary.getProperty("failed"));
                skipped += Long.parseLong(summary.getProperty("skipped"));
                aborted += Long.parseLong(summary.getProperty("aborted"));
                String enableError = Logs.enableError(log, pluginName);
                if (enableError != null) {
                    // Spigot keeps a plugin enabled when its onEnable throws, so the tests ran; the
                    // plugin is still broken on this server.
                    throw new GradleException("The plugin under test (" + pluginName + ") failed while enabling on " + run
                            + ":\n" + enableError + "\nServer log: " + log + "\n" + Logs.problems(log, 30));
                }
                File failuresFile = new File(resultsDir, "failures.txt");
                if (failuresFile.isFile()) {
                    failures.append(Files.readString(failuresFile.toPath()));
                }
            }
            LOGGER.lifecycle("[{}] {}: {} tests, {} passed, {} failed, {} skipped, {} aborted ({} s{})",
                    version, server, found, succeeded, failed, skipped, aborted, took.toSeconds(),
                    runs.size() > 1 ? ", with a restart" : "");
            if (failed > 0) {
                throw new GradleException(failed + " server test(s) failed on " + version + ":\n\n" + failures
                        + "JUnit XML report: " + new File(runs.get(0), "junit-xml")
                        + (runs.size() > 1 ? " and " + new File(runs.get(1), "junit-xml") : "")
                        + "\nServer log: " + new File(runs.get(0), "server.log"));
            }
            if (found == 0) {
                throw new GradleException("No server tests were found. Put them in src/serverTest/java.");
            }
        }
    }
}
