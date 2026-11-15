package io.github.larslewerenz.spigottesting.gradle;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** Downloads the plugins listed in {@code spigotTesting.pluginUrls}, cached per URL. */
@DisableCachingByDefault(because = "Downloads are cached outside the build, per machine")
public abstract class DownloadPlugins extends DefaultTask {

    @Input
    public abstract ListProperty<String> getUrls();

    @Internal
    public abstract DirectoryProperty getCacheDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract FileSystemOperations getFiles();

    @TaskAction
    public void download() throws IOException {
        File out = getOutputDirectory().get().getAsFile();
        getFiles().delete(spec -> spec.delete(out));
        out.mkdirs();
        for (String url : getUrls().get()) {
            String key = sha256(url).substring(0, 16);
            String name = fileName(url);
            File cached = new File(getCacheDirectory().get().getAsFile(), key + "/" + name);
            if (!cached.isFile()) {
                getLogger().lifecycle("Downloading plugin {}", url);
                SpigotVersions.download(url, cached);
            }
            Files.copy(cached.toPath(), new File(out, key + "-" + name).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String fileName(String url) {
        String path = URI.create(url).getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        return last.endsWith(".jar") ? last : "plugin.jar";
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
