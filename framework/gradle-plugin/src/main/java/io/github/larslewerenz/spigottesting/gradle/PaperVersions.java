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

    /** The Java version Paper declares as minimum for the given version. */
    static int requiredJava(String version, File cacheDir) {
        File metadata = new File(cacheDir, "paper/versions/" + version + ".json");
        if (!metadata.isFile()) {
            SpigotVersions.download(API + version, metadata);
        }
        Matcher m = JAVA_MINIMUM.matcher(read(metadata));
        if (!m.find()) {
            throw new GradleException("No Java version in PaperMC's metadata for " + version + " (" + metadata + ")");
        }
        return Integer.parseInt(m.group(1));
    }

    /** The latest build's download URL and SHA-256. */
    static String[] latestBuild(String version, File cacheDir) {
        File build = new File(cacheDir, "paper/builds/" + version + "-latest.json");
        SpigotVersions.download(API + version + "/builds/latest", build);
        Matcher m = DOWNLOAD.matcher(read(build));
        if (!m.find()) {
            throw new GradleException("No server download in PaperMC's latest build of " + version + " (" + build + ")");
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
