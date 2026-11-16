package io.github.larslewerenz.spigottesting.gradle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The plugin on the Gradle versions it supports: it applies, registers its tasks and builds the
 * test harness (which needs the bundled runtime and JUnit). Gradle downloads each version once.
 */
class GradleVersionsTest {

    @TempDir
    Path project;

    @ParameterizedTest(name = "Gradle {0}")
    @ValueSource(strings = {"8.5", "8.14.3", "9.0.0"})
    void worksOn(String gradleVersion) throws IOException {
        Files.writeString(project.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"\n");
        Files.writeString(project.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("io.github.lars-lewerenz.spigot-testing")
                }
                repositories { mavenCentral() }
                spigotTesting {
                    versions.set(listOf("1.8.8"))
                    paperVersions.set(listOf("26.3"))
                }
                """);
        Files.createDirectories(project.resolve("src/main/resources"));
        Files.writeString(project.resolve("src/main/resources/plugin.yml"), "name: Fixture\nversion: '1'\nmain: x.Fixture\n");
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

        BuildResult result = GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments("tasks", "--all", "serverTestHarnessJar")
                .build();

        assertTrue(result.getOutput().contains("serverTest_1_8_8"), result.getOutput());
        assertTrue(result.getOutput().contains("serverTest_paper_26_3"), result.getOutput());
        try (ZipFile jar = new ZipFile(project.resolve("build/spigot-testing/spigot-testing-harness.jar").toFile())) {
            assertTrue(jar.getEntry("x/FixtureTest.class") != null, "test class missing");
            assertTrue(jar.getEntry("io/github/larslewerenz/spigottesting/ServerTest.class") != null, "runtime missing");
        }
    }
}
