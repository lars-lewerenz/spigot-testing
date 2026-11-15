package io.github.larslewerenz.spigottesting.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;

/** Looks up which Java version a Spigot version needs, using BuildTools' version metadata. */
final class SpigotVersions {

    static final String VERSIONS_URL = "https://hub.spigotmc.org/versions/";

    private static final Pattern JAVA_VERSIONS = Pattern.compile("\"javaVersions\"\\s*:\\s*\\[\\s*(\\d+)(?:\\s*,\\s*(\\d+))?");
    /** Long-term support releases: available for every platform and preinstalled on CI runners. */
    private static final int[] LTS = {8, 11, 17, 21, 25};

    private SpigotVersions() {
    }

    /**
     * The Java version to build and run the given Spigot version with. BuildTools' metadata lists
     * the supported range as class file versions (52 = Java 8); versions without it (1.8 - 1.11)
     * use Java 8.
     */
    static int requiredJava(String version, File cacheDir) {
        File metadata = new File(cacheDir, "versions/" + version + ".json");
        if (!metadata.isFile()) {
            download(VERSIONS_URL + version + ".json", metadata);
        }
        try {
            return javaFromMetadata(Files.readString(metadata.toPath()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static int javaFromMetadata(String json) {
        Matcher m = JAVA_VERSIONS.matcher(json);
        if (!m.find()) {
            return 8;
        }
        int min = Integer.parseInt(m.group(1)) - 44;
        int max = m.group(2) == null ? Integer.MAX_VALUE : Integer.parseInt(m.group(2)) - 44;
        return pickJava(min, max);
    }

    /**
     * The lowest LTS release in [min, max], or min if the range has none. E.g. Spigot 1.17 allows
     * Java 16-17: 17 is the better choice, since Java 16 builds don't exist for every platform.
     */
    static int pickJava(int min, int max) {
        for (int lts : LTS) {
            if (lts >= min && lts <= max) {
                return lts;
            }
        }
        return Math.max(8, min);
    }

    static void download(String url, File target) {
        try {
            Files.createDirectories(target.getParentFile().toPath());
            File partial = new File(target.getPath() + ".part");
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<java.nio.file.Path> response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofFile(partial.toPath()));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(partial.toPath());
                throw new GradleException("Downloading " + url + " failed with HTTP " + response.statusCode()
                        + (url.startsWith(VERSIONS_URL) ? " (is the Spigot version spelled correctly?)" : ""));
            }
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Downloading " + url + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while downloading " + url, e);
        }
    }
}
