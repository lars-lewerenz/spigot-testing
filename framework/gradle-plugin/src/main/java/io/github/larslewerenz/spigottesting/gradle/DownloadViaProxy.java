package io.github.larslewerenz.spigottesting.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import java.io.File;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

/**
 * Downloads ViaProxy, which lets the bots (fake players) join every server version, once per
 * machine and before the test servers start, so parallel servers don't all download it.
 */
@DisableCachingByDefault(because = "ViaProxy is cached outside the build, per machine")
public abstract class DownloadViaProxy extends DefaultTask {

    @Input
    public abstract Property<String> getUrl();

    /** In the cache shared by all projects and builds, so not a task output. */
    @Internal
    public abstract RegularFileProperty getJar();

    @TaskAction
    public void download() {
        File jar = getJar().get().getAsFile();
        CacheLock.withLock(jar, () -> {
            if (!jar.isFile()) {
                getLogger().lifecycle("Downloading ViaProxy for the bot clients");
                SpigotVersions.download(getUrl().get(), jar);
            }
        });
    }
}
