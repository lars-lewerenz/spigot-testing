package io.github.larslewerenz.spigottesting.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The harness plugin's plugin.yml and its list of test classes. */
class GenerateHarnessResourcesTest {

    @TempDir
    Path dir;

    @Test
    void pluginNameVariants() {
        assertEquals("Greeter", GenerateHarnessResources.pluginName("name: Greeter\nversion: 1.0\n"));
        assertEquals("My Plugin", GenerateHarnessResources.pluginName("main: a.B\nname: 'My Plugin'\n"));
        assertEquals("Quoted", GenerateHarnessResources.pluginName("name: \"Quoted\" # comment\n"));
        // only the top-level key counts, not e.g. a command's name
        assertEquals("Top", GenerateHarnessResources.pluginName("commands:\n  x:\n    name: nested\nname: Top\n"));
        assertNull(GenerateHarnessResources.pluginName("version: 1.0\n"));
    }

    @Test
    void writesPluginYmlAndTestList() throws IOException {
        Path classes = Files.createDirectories(dir.resolve("classes/com/example"));
        Files.createFile(classes.resolve("FooTest.class"));
        Files.createFile(classes.resolve("FooTest$Nested.class")); // nested classes are found through their outer class
        Files.createFile(dir.resolve("classes/BarTest.class"));

        Project project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build();
        GenerateHarnessResources task = project.getTasks().register("generate", GenerateHarnessResources.class).get();
        task.getPluginJar().set(jar("plugin.jar", "name: Greeter\nmain: x.Y\n").toFile());
        task.getOtherPlugins().from(jar("vault.jar", "name: Vault\n"), jar("papi.jar", "name: PlaceholderAPI\n"));
        task.getTestClassesDirs().from(dir.resolve("classes"));
        task.getOutputDirectory().set(dir.resolve("out").toFile());

        task.generate();

        List<String> pluginYml = Files.readAllLines(dir.resolve("out/plugin.yml"));
        assertEquals("depend: ['Greeter']", pluginYml.get(4));
        assertEquals("softdepend: ['Vault', 'PlaceholderAPI']", pluginYml.get(5));
        assertEquals(List.of("BarTest", "com.example.FooTest"), Files.readAllLines(dir.resolve("out/spigot-testing/tests.txt")));
    }

    private Path jar(String name, String pluginYml) throws IOException {
        Path jar = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("plugin.yml"));
            zip.write(pluginYml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return jar;
    }
}
