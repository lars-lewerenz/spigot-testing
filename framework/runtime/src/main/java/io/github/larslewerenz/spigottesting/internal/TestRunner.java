package io.github.larslewerenz.spigottesting.internal;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener;

/** Runs the test classes listed in the harness jar with the JUnit Platform launcher. */
final class TestRunner {

    private static final String TEST_LIST = "spigot-testing/tests.txt";
    /** Written to the output directory when there are {@code @AfterRestart} tests to run. */
    static final String RESTART_REQUEST = "restart-requested";

    private final JavaPlugin plugin;
    private final File outputDir;

    TestRunner(JavaPlugin plugin, File outputDir) {
        this.plugin = plugin;
        this.outputDir = outputDir;
    }

    void run() throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        List<DiscoverySelector> selectors = new ArrayList<>();
        for (String className : readTestClassNames(classLoader)) {
            selectors.add(selectClass(classLoader, className));
        }
        plugin.getLogger().info(ProgressListener.MARKER + "INFO Running " + selectors.size()
                + " test class(es) on " + Bukkit.getName() + " " + Bukkit.getVersion());

        boolean afterRestart = RestartPhase.current();
        LauncherDiscoveryRequest request = request(selectors, afterRestart);
        // Only the listeners registered below: auto-registered ones (e.g. Open Test Reporting)
        // are not needed and may not run on the server's Java version.
        Launcher launcher = LauncherFactory.create(LauncherConfig.builder()
                .enableTestExecutionListenerAutoRegistration(false)
                .enableLauncherSessionListenerAutoRegistration(false)
                .enableLauncherDiscoveryListenerAutoRegistration(false)
                .enablePostDiscoveryFilterAutoRegistration(false)
                .build());

        if (!afterRestart && launcher.discover(request(selectors, true)).containsTests()) {
            // Tell the Gradle task to start the server again for the @AfterRestart tests.
            Files.createDirectories(outputDir.toPath());
            Files.write(new File(outputDir, RESTART_REQUEST).toPath(), new byte[0]);
        }

        File xmlDir = new File(outputDir, "junit-xml");
        Files.createDirectories(xmlDir.toPath());
        SummaryGeneratingListener summary = new SummaryGeneratingListener();
        StringWriter xmlErrors = new StringWriter();
        launcher.execute(request,
                summary,
                new LegacyXmlReportGeneratingListener(xmlDir.toPath(), new PrintWriter(xmlErrors)),
                new ProgressListener(plugin.getLogger()));
        if (xmlErrors.getBuffer().length() > 0) {
            plugin.getLogger().warning("Writing the XML report failed: " + xmlErrors);
        }
        writeSummary(summary.getSummary());
    }

    private static LauncherDiscoveryRequest request(List<DiscoverySelector> selectors, boolean afterRestart) {
        return LauncherDiscoveryRequestBuilder.request()
                .selectors(selectors)
                .filters(new RestartPhase(afterRestart))
                .configurationParameter("junit.jupiter.extensions.autodetection.enabled", "true")
                .build();
    }

    private static List<String> readTestClassNames(ClassLoader classLoader) throws IOException {
        List<String> names = new ArrayList<>();
        try (InputStream in = classLoader.getResourceAsStream(TEST_LIST)) {
            if (in == null) {
                throw new IOException(TEST_LIST + " is missing from the harness jar");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    names.add(line.trim());
                }
            }
        }
        return names;
    }

    private void writeSummary(TestExecutionSummary summary) throws IOException {
        Properties props = new Properties();
        props.setProperty("server", Bukkit.getName() + " " + Bukkit.getVersion());
        props.setProperty("found", String.valueOf(summary.getTestsFoundCount()));
        props.setProperty("succeeded", String.valueOf(summary.getTestsSucceededCount()));
        props.setProperty("failed", String.valueOf(summary.getTotalFailureCount()));
        props.setProperty("skipped", String.valueOf(summary.getTestsSkippedCount()));
        props.setProperty("aborted", String.valueOf(summary.getTestsAbortedCount()));
        try (OutputStream out = Files.newOutputStream(new File(outputDir, "summary.properties").toPath())) {
            props.store(out, null);
        }

        StringWriter failures = new StringWriter();
        PrintWriter writer = new PrintWriter(failures);
        for (TestExecutionSummary.Failure failure : summary.getFailures()) {
            writer.println(failure.getTestIdentifier().getLegacyReportingName()
                    + " [" + failure.getTestIdentifier().getUniqueId() + "]");
            failure.getException().printStackTrace(writer);
            writer.println();
        }
        writer.flush();
        Files.write(new File(outputDir, "failures.txt").toPath(), failures.toString().getBytes(StandardCharsets.UTF_8));
    }
}
