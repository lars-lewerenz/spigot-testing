package io.github.larslewerenz.spigottesting.gradle;

import java.time.Duration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/** The {@code spigotTesting { }} block. */
public abstract class SpigotTestingExtension {

    /** Spigot versions to run the server tests on, e.g. {@code "1.8.8"}, {@code "1.21.11"}. */
    public abstract ListProperty<String> getVersions();

    /**
     * Whether you accept the Minecraft EULA (https://aka.ms/MinecraftEULA), which is required to
     * run the servers. Defaults to the Gradle property {@code spigotTesting.acceptEula}.
     */
    public abstract Property<Boolean> getAcceptEula();

    /** The plugin jar to test. Defaults to the output of the {@code jar} task. */
    public abstract RegularFileProperty getPluginJar();

    /** JVM arguments for the test servers. Defaults to {@code -Xmx2G}. */
    public abstract ListProperty<String> getJvmArgs();

    /** How long one server run may take before it is killed. Defaults to 10 minutes. */
    public abstract Property<Duration> getTimeout();

    /**
     * Where BuildTools and the built server jars are cached. Defaults to
     * {@code ~/.gradle/caches/spigot-testing}.
     */
    public abstract DirectoryProperty getCacheDirectory();
}
