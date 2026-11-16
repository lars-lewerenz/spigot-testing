package io.github.larslewerenz.spigottesting.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Writes the harness plugin's {@code plugin.yml} (depending on the plugin under test) and the list
 * of test classes to run.
 */
@DisableCachingByDefault(because = "Generating two small files is faster than the build cache")
public abstract class GenerateHarnessResources extends DefaultTask {

    static final String PLUGIN_YML = "plugin.yml";
    static final String PAPER_PLUGIN_YML = "paper-plugin.yml";

    private static final Pattern NAME = Pattern.compile("(?m)^name\\s*:\\s*['\"]?([^'\"#\\r\\n]+?)['\"]?\\s*(#.*)?$");

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getPluginJar();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestClassesDirs();

    /** Other plugins on the test server: the harness loads after them, so tests can use them. */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getOtherPlugins();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        Path out = Files.createDirectories(getOutputDirectory().get().getAsFile().toPath());
        File pluginJar = getPluginJar().get().getAsFile();
        String target = readPluginName(pluginJar);
        List<String> others = new ArrayList<>();
        for (File jar : getOtherPlugins().getFiles()) {
            others.add(readPluginName(jar));
        }

        // Paper plugins (paper-plugin.yml only) get a Paper plugin as harness: Paper isolates
        // their classes, and only a Paper plugin dependency with join-classpath can see them.
        Files.deleteIfExists(out.resolve(PLUGIN_YML));
        Files.deleteIfExists(out.resolve(PAPER_PLUGIN_YML));
        if (isPaperPlugin(pluginJar)) {
            List<String> lines = new ArrayList<>(List.of(
                    "name: SpigotTesting",
                    "version: '1.0'",
                    "main: io.github.larslewerenz.spigottesting.internal.HarnessPlugin",
                    "api-version: '1.19'",
                    "dependencies:",
                    "  server:"));
            lines.addAll(paperDependency(target, true));
            for (String other : others) {
                lines.addAll(paperDependency(other, false));
            }
            lines.add("");
            Files.writeString(out.resolve(PAPER_PLUGIN_YML), String.join("\n", lines));
        } else {
            Files.writeString(out.resolve(PLUGIN_YML), String.join("\n",
                    "name: SpigotTesting",
                    "version: '1.0'",
                    "main: io.github.larslewerenz.spigottesting.internal.HarnessPlugin",
                    "api-version: '1.13'",
                    "depend: [" + quote(target) + "]",
                    "softdepend: [" + String.join(", ", others.stream().map(GenerateHarnessResources::quote).toList()) + "]",
                    ""));
        }
        // Read by the harness to check that the plugin under test is enabled.
        Files.createDirectories(out.resolve("spigot-testing"));
        Files.writeString(out.resolve("spigot-testing/plugin-under-test.txt"), target + "\n");

        List<String> classes = new ArrayList<>();
        for (File dir : getTestClassesDirs().getFiles()) {
            if (!dir.isDirectory()) {
                continue;
            }
            Path root = dir.toPath();
            try (Stream<Path> files = Files.walk(root)) {
                files.map(p -> root.relativize(p).toString().replace(File.separatorChar, '/'))
                        .filter(p -> p.endsWith(".class") && !p.contains("$") && !p.endsWith("module-info.class"))
                        .map(p -> p.substring(0, p.length() - ".class".length()).replace('/', '.'))
                        .forEach(classes::add);
            }
        }
        classes.sort(null);
        Path list = out.resolve("spigot-testing/tests.txt");
        Files.createDirectories(list.getParent());
        Files.write(list, classes, StandardCharsets.UTF_8);
    }

    private static List<String> paperDependency(String name, boolean required) {
        return List.of(
                "    " + quote(name) + ":",
                "      load: BEFORE",
                "      required: " + required,
                "      join-classpath: true");
    }

    /** Whether the jar is a Paper plugin: a paper-plugin.yml and no plugin.yml. */
    static boolean isPaperPlugin(File jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            return zip.getEntry(PLUGIN_YML) == null && zip.getEntry(PAPER_PLUGIN_YML) != null;
        }
    }

    /** The top-level {@code name} of a plugin.yml, or null. */
    static String pluginName(String pluginYml) {
        Matcher m = NAME.matcher(pluginYml);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String quote(String yamlString) {
        return "'" + yamlString.replace("'", "''") + "'";
    }

    /** The name in a plugin jar's plugin.yml, or its paper-plugin.yml if it has only that. */
    static String readPluginName(File pluginJar) throws IOException {
        try (ZipFile zip = new ZipFile(pluginJar)) {
            ZipEntry entry = zip.getEntry(PLUGIN_YML);
            if (entry == null) {
                entry = zip.getEntry(PAPER_PLUGIN_YML);
            }
            if (entry == null) {
                throw new GradleException(pluginJar + " has neither a plugin.yml nor a paper-plugin.yml");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                String name = pluginName(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                if (name == null) {
                    throw new GradleException("No 'name' in the " + entry.getName() + " of " + pluginJar);
                }
                return name;
            }
        }
    }
}
