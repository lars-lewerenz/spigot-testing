package io.github.larslewerenz.spigottesting.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;

/** Paper builds from PaperMC's download API (https://fill.papermc.io). */
final class PaperVersions {

    static final String API = "https://fill.papermc.io/v3/projects/paper/versions/";

    private static final Pattern JAVA_MINIMUM = Pattern.compile("\"java\"\\s*:\\s*\\{\\s*\"version\"\\s*:\\s*\\{\\s*\"minimum\"\\s*:\\s*(\\d+)");
    private static final Pattern DOWNLOAD = Pattern.compile(
            "\"server:default\"\\s*:\\s*\\{.*?\"sha256\"\\s*:\\s*\"([0-9a-f]{64})\".*?\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL);

    private PaperVersions() {
    }

    /**
     * The Java version to run the given Paper version with: the lowest LTS release at or above the
     * minimum PaperMC declares (PaperMC gives no maximum).
     */
    static int requiredJava(String version, File cacheDir) {
        File metadata = new File(cacheDir, "paper/versions/" + version + ".json");
        if (!metadata.isFile()) {
            SpigotVersions.download(API + version, metadata);
        }
        return javaFromMetadata(read(metadata), version);
    }

    static int javaFromMetadata(String json, String version) {
        Matcher m = JAVA_MINIMUM.matcher(json);
        if (!m.find()) {
            throw new GradleException("No Java version in PaperMC's metadata for " + version);
        }
        int minimum = Integer.parseInt(m.group(1));
        return SpigotVersions.pickJava(minimum, Integer.MAX_VALUE);
    }

    /** The latest build's download URL and SHA-256. */
    static String[] latestBuild(String version, File cacheDir) {
        File build = new File(cacheDir, "paper/builds/" + version + "-latest.json");
        SpigotVersions.download(API + version + "/builds/latest", build);
        return downloadFromBuild(read(build), version);
    }

    /** URL and SHA-256 of the server jar in a build's metadata. */
    static String[] downloadFromBuild(String json, String version) {
        Matcher m = DOWNLOAD.matcher(json);
        if (!m.find()) {
            throw new GradleException("No server download in PaperMC's latest build of " + version);
        }
        return new String[] {m.group(2), m.group(1)};
    }

    private static String read(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
