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

    private static final Pattern JAVA_VERSIONS = Pattern.compile("\"javaVersions\"\\s*:\\s*\\[\\s*(\\d+)");

    private SpigotVersions() {
    }

    /**
     * The lowest Java version the given Spigot version supports. The metadata lists class file
     * versions (52 = Java 8); versions without the field (1.8 - 1.11) are built and run with Java 8.
     */
    static int requiredJava(String version, File cacheDir) {
        File metadata = new File(cacheDir, "versions/" + version + ".json");
        if (!metadata.isFile()) {
            download(VERSIONS_URL + version + ".json", metadata);
        }
        try {
            Matcher m = JAVA_VERSIONS.matcher(Files.readString(metadata.toPath()));
            return m.find() ? Math.max(8, Integer.parseInt(m.group(1)) - 44) : 8;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
