package io.github.larslewerenz.spigottesting.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Applies the plugin to a fresh project with Gradle TestKit. No servers are started. */
class PluginFunctionalTest {

    @TempDir
    Path project;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(project.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"\n");
        Files.writeString(project.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("io.github.lars-lewerenz.spigot-testing")
                }
                spigotTesting {
                    versions = listOf("1.8.8", "1.21.11")
                    paperVersions = listOf("26.3")
                }
                """);
    }

    private GradleRunner gradle(String... arguments) {
        return GradleRunner.create().withProjectDir(project.toFile()).withPluginClasspath().withArguments(arguments);
    }

    @Test
    void registersTasksPerServer() {
        String tasks = gradle("tasks", "--all").build().getOutput();

        for (String task : new String[] {
                "serverTest ", "serverTest_1_8_8", "serverTest_1_21_11", "serverTest_paper_26_3",
                "buildSpigot_1_8_8", "buildSpigot_1_21_11", "downloadPaper_26_3", "checkMinecraftEula"}) {
            assertTrue(tasks.contains(task), task + " is missing:\n" + tasks);
        }
        assertFalse(tasks.contains("buildSpigot_26_3"), "Paper versions must not build Spigot");
    }

    @Test
    void versionsCanBeOverriddenOnTheCommandLine() {
        String tasks = gradle("tasks", "--all", "-PspigotTesting.versions=1.12.2", "-PspigotTesting.paperVersions=")
                .build().getOutput();

        assertTrue(tasks.contains("serverTest_1_12_2"), tasks);
        assertFalse(tasks.contains("serverTest_1_8_8"), tasks);
        assertFalse(tasks.contains("serverTest_paper_"), tasks);
    }

    private void addCustomServer(String settings) throws IOException {
        Files.writeString(project.resolve("build.gradle.kts"), Files.readString(project.resolve("build.gradle.kts")) + """
                spigotTesting {
                    customServers {
                        register("windspigot") {
                %s
                        }
                    }
                }
                """.formatted(settings));
    }

    @Test
    void customServersGetATaskOfTheirOwnWithoutBuildToolsOrDownload() throws IOException {
        Files.writeString(project.resolve("windspigot.jar"), "not a real server");
        addCustomServer("""
                            serverJar = file("windspigot.jar")
                            minecraftVersion = "1.8.8"
                            javaVersion = 17
                """);

        String tasks = gradle("tasks", "--all").build().getOutput();
        assertTrue(tasks.contains("serverTest_windspigot - Runs the server tests on windspigot."), tasks);
        assertFalse(tasks.contains("buildSpigot_windspigot"), tasks);

        String plan = gradle("serverTest", "--dry-run", "-PspigotTesting.acceptEula=true").build().getOutput();
        assertTrue(plan.contains(":serverTest_windspigot SKIPPED"), "serverTest runs it too:\n" + plan);
    }

    @Test
    void customServersCanBeChosenOnTheCommandLine() throws IOException {
        addCustomServer("""
                            serverJar = file("windspigot.jar")
                            minecraftVersion = "1.8.8"
                """);

        String none = gradle("tasks", "--all", "-PspigotTesting.customServers=").build().getOutput();
        assertFalse(none.contains("serverTest_windspigot"), none);

        String unknown = gradle("tasks", "--all", "-PspigotTesting.customServers=pandaspigot").buildAndFail().getOutput();
        assertTrue(unknown.contains("no custom server named \"pandaspigot\" (declared: [windspigot])"), unknown);
    }

    @Test
    void customServersNeedAJarAndAMinecraftVersion() throws IOException {
        addCustomServer("""
                            minecraftVersion = "1.8.8"
                """);
        String noJar = gradle("tasks").buildAndFail().getOutput();
        assertTrue(noJar.contains("spigotTesting.customServers.windspigot: set serverJar"), noJar);

        setUp();
        addCustomServer("""
                            serverJar = file("windspigot.jar")
                """);
        String noVersion = gradle("tasks").buildAndFail().getOutput();
        assertTrue(noVersion.contains("spigotTesting.customServers.windspigot: set minecraftVersion"), noVersion);

        setUp();
        addCustomServer("""
                            serverJar = file("windspigot.jar")
                            minecraftVersion = "1.8.8"
                            type = "bungeecord"
                """);
        String wrongType = gradle("tasks").buildAndFail().getOutput();
        assertTrue(wrongType.contains("type must be \"spigot\" or \"paper\", not \"bungeecord\""), wrongType);
    }

    @Test
    void eulaCheckFailsWithoutAcceptance() {
        BuildResult result = gradle("checkMinecraftEula").buildAndFail();

        assertTrue(result.getOutput().contains("https://aka.ms/MinecraftEULA"), result.getOutput());
        assertTrue(result.getOutput().contains("spigotTesting.acceptEula=true"), result.getOutput());
    }

    @Test
    void eulaCheckPassesWhenAccepted() {
        gradle("checkMinecraftEula", "-PspigotTesting.acceptEula=true").build();
    }

    @Test
    void harnessJarWorksWithTheConfigurationCache() throws IOException {
        Files.writeString(project.resolve("build.gradle.kts"), Files.readString(project.resolve("build.gradle.kts"))
                + "repositories { mavenCentral() }\n");
        Path resources = Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(resources.resolve("plugin.yml"), "name: Fixture\nversion: '1'\nmain: x.Fixture\n");
        Path test = Files.createDirectories(project.resolve("src/serverTest/java/x"));
        Files.writeString(test.resolve("FixtureTest.java"), """
                package x;
                class FixtureTest {
                    @org.junit.jupiter.api.Test
                    void runs() {
                        io.github.larslewerenz.spigottesting.ServerTest.class.getName();
                    }
                }
                """);

        BuildResult first = gradle("serverTestHarnessJar", "--configuration-cache").build();
        BuildResult second = gradle("serverTestHarnessJar", "--configuration-cache", "--rerun-tasks").build();

        assertTrue(first.getOutput().contains("Configuration cache entry stored"), first.getOutput());
        assertTrue(second.getOutput().contains("Reusing configuration cache"), second.getOutput());
        try (var jar = new java.util.zip.ZipFile(project.resolve("build/spigot-testing/spigot-testing-harness.jar").toFile())) {
            assertTrue(jar.getEntry("x/FixtureTest.class") != null, "test class missing");
            assertTrue(jar.getEntry("io/github/larslewerenz/spigottesting/ServerTest.class") != null, "runtime missing");
            assertTrue(jar.getEntry("org/junit/platform/launcher/Launcher.class") != null, "JUnit missing");
            assertTrue(jar.getEntry("plugin.yml") != null, "plugin.yml missing");
        }
    }

    @Test
    void unitTestsGetJunitAndTheCompileOnlyApi() throws IOException {
        Files.writeString(project.resolve("build.gradle.kts"), Files.readString(project.resolve("build.gradle.kts")) + """
                repositories { mavenCentral() }
                dependencies { compileOnly("com.google.code.gson:gson:2.11.0") }
                """);
        Path test = Files.createDirectories(project.resolve("src/test/java/x"));
        Files.writeString(test.resolve("UsesCompileOnlyTest.java"), """
                package x;
                class UsesCompileOnlyTest {
                    @org.junit.jupiter.api.Test
                    void compileOnlyIsOnTheTestClasspath() {
                        org.junit.jupiter.api.Assertions.assertEquals("{}", new com.google.gson.JsonObject().toString());
                    }
                }
                """);

        BuildResult result = gradle("test").build();

        assertTrue(result.getOutput().contains("BUILD SUCCESSFUL"), result.getOutput());
    }
}
