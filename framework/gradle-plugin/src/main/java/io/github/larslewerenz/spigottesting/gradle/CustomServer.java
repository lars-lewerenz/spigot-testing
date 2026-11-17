package io.github.larslewerenz.spigottesting.gradle;

import org.gradle.api.Named;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * A server from your own jar instead of BuildTools or PaperMC, e.g. a fork such as WindSpigot that your
 * network runs. Declared in {@code spigotTesting { customServers { register("name") { ... } } }}; the
 * tests run on it in the task {@code serverTest_<name>}.
 */
public abstract class CustomServer implements Named {

    /** The name, used for the task ({@code serverTest_<name>}), the output and a server-file layer. */
    @Override
    public abstract String getName();

    /** The server jar to run, e.g. {@code file("libs/windspigot.jar")}. Required. */
    public abstract RegularFileProperty getServerJar();

    /**
     * The Minecraft version the server implements, e.g. {@code "1.8.8"}. Required: it's what
     * {@code ServerTest.minecraftVersion()} and {@code @EnabledForMinecraftRange} see, it picks the
     * {@code server-<version>} file layers and, unless set otherwise, the Java version.
     */
    public abstract Property<String> getMinecraftVersion();

    /**
     * What the server is a fork of: {@code "spigot"} (the default) or {@code "paper"}. It's what
     * {@code ServerTest.serverType()} and {@code @EnabledOnServer} see, and it picks the
     * {@code server-<type>} file layers.
     */
    public abstract Property<String> getType();

    /**
     * The Java version to run the server on. Defaults to {@code javaVersions} for its Minecraft version,
     * and then to the Java Spigot or Paper of that version needs.
     */
    public abstract Property<Integer> getJavaVersion();
}
