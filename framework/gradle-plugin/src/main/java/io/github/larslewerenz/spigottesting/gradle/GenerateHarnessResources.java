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
public abstract class GenerateHarnessResources extends DefaultTask {

    private static final Pattern NAME = Pattern.compile("(?m)^name\\s*:\\s*['\"]?([^'\"#\\r\\n]+?)['\"]?\\s*(#.*)?$");

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getPluginJar();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestClassesDirs();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        Path out = getOutputDirectory().get().getAsFile().toPath();
        String target = readPluginName(getPluginJar().get().getAsFile());

        Files.writeString(out.resolve("plugin.yml"), String.join("\n",
                "name: SpigotTesting",
                "version: '1.0'",
                "main: io.github.larslewerenz.spigottesting.internal.HarnessPlugin",
                "api-version: '1.13'",
                "depend: ['" + target.replace("'", "''") + "']",
                ""));

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

    private static String readPluginName(File pluginJar) throws IOException {
        try (ZipFile zip = new ZipFile(pluginJar)) {
            ZipEntry entry = zip.getEntry("plugin.yml");
            if (entry == null) {
                throw new GradleException(pluginJar + " has no plugin.yml");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                Matcher m = NAME.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                if (!m.find()) {
                    throw new GradleException("No 'name' in the plugin.yml of " + pluginJar);
                }
                return m.group(1).trim();
            }
        }
    }
}
