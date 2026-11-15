package io.github.larslewerenz.spigottesting.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.gradle.api.DefaultTask;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Downloads the latest Paper build of a Minecraft version, checks its SHA-256 and caches it, so
 * each version is downloaded once per machine. Delete the cached jar to get a newer build.
 */
@DisableCachingByDefault(because = "Paper jars are cached outside the build, per machine")
public abstract class DownloadPaperTask extends DefaultTask {

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Internal
    public abstract DirectoryProperty getCacheDirectory();

    @OutputFile
    public abstract RegularFileProperty getServerJar();

    @TaskAction
    public void download() throws IOException {
        File jar = getServerJar().get().getAsFile();
        if (jar.isFile()) {
            getLogger().info("Using cached {}", jar);
            return;
        }
        String version = getMinecraftVersion().get();
        String[] build = PaperVersions.latestBuild(version, getCacheDirectory().get().getAsFile());
        getLogger().lifecycle("Downloading Paper {}", version);
        File partial = new File(jar.getPath() + ".download");
        SpigotVersions.download(build[0], partial);
        String actual = sha256(partial);
        if (!actual.equals(build[1])) {
            Files.deleteIfExists(partial.toPath());
            throw new GradleException("Checksum mismatch for " + build[0] + ": expected " + build[1] + ", got " + actual);
        }
        Files.move(partial.toPath(), jar.toPath());
    }

    private static String sha256(File file) throws IOException {
        try (InputStream in = new DigestInputStream(Files.newInputStream(file.toPath()), MessageDigest.getInstance("SHA-256"))) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
