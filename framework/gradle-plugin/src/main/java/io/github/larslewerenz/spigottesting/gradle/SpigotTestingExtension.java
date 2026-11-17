package io.github.larslewerenz.spigottesting.gradle;

import java.time.Duration;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/** The {@code spigotTesting { }} block. */
public abstract class SpigotTestingExtension {

    /** Spigot versions to run the server tests on, e.g. {@code "1.8.8"}, {@code "1.21.11"}. */
    public abstract ListProperty<String> getVersions();

    /**
     * Paper versions to run the server tests on as well, e.g. {@code "1.8.8"}, {@code "26.3"}.
     * The latest build of each is downloaded from PaperMC once and cached.
     */
    public abstract ListProperty<String> getPaperVersions();

    /**
     * Servers from your own jars, e.g. a fork your network runs:
     * {@code customServers { register("windspigot") { serverJar = file("libs/windspigot.jar"); minecraftVersion = "1.8.8" } }}.
     * See {@link CustomServer}.
     */
    public abstract NamedDomainObjectContainer<CustomServer> getCustomServers();

    /**
     * Whether you accept the Minecraft EULA (https://aka.ms/MinecraftEULA), which is required to
     * run the servers. Defaults to the Gradle property {@code spigotTesting.acceptEula}.
     */
    public abstract Property<Boolean> getAcceptEula();

    /**
     * Download URLs of other plugins to install on the test servers, for plugins that are not
     * available from a Maven repository (those go into the {@code serverTestPlugin} configuration).
     * Downloaded once and cached.
     */
    public abstract ListProperty<String> getPluginUrls();

    /** The plugin jar to test. Defaults to the output of the {@code jar} task. */
    public abstract RegularFileProperty getPluginJar();

    /**
     * The Java version to run a server on, by Minecraft version (for Spigot and Paper alike), e.g.
     * {@code javaVersions.put("1.8.8", 17)}. Servers not listed run on the oldest LTS Java their
     * version supports. BuildTools always uses that one, so only the run changes. Java is taken
     * from your Gradle toolchains (installed or auto-provisioned).
     */
    public abstract MapProperty<String, Integer> getJavaVersions();

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
