package io.github.larslewerenz.spigottesting.gradle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;

/**
 * Builds a Spigot server jar with BuildTools (Spigot cannot be downloaded directly). The jar is
 * cached outside the project, so each version is built only once per machine. BuildTools runs in
 * a Gradle worker, so several versions build in parallel.
 */
@DisableCachingByDefault(because = "Spigot jars are cached outside the build, per machine")
public abstract class BuildSpigotTask extends DefaultTask {

    static final String BUILD_TOOLS_URL =
            "https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar";

    @Input
    public abstract Property<String> getMinecraftVersion();

    /** BuildTools refuses to build a version with a Java version outside the supported range. */
    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    @Internal
    public abstract DirectoryProperty getCacheDirectory();

    @OutputFile
    public abstract RegularFileProperty getServerJar();

    @Inject
    protected abstract WorkerExecutor getWorkers();

    @TaskAction
    public void build() {
        File serverJar = getServerJar().get().getAsFile();
        if (serverJar.isFile()) {
            getLogger().info("Using cached {}", serverJar);
            return;
        }
        // Downloaded here, before any worker starts, so parallel builds don't race for it.
        File buildTools = new File(getCacheDirectory().get().getAsFile(), "BuildTools.jar");
        if (!buildTools.isFile()) {
            getLogger().lifecycle("Downloading BuildTools");
            SpigotVersions.download(BUILD_TOOLS_URL, buildTools);
        }
        JavaLauncher launcher = getJavaLauncher().get();
        getWorkers().noIsolation().submit(Work.class, p -> {
            p.getMinecraftVersion().set(getMinecraftVersion());
            p.getJavaExecutable().set(launcher.getExecutablePath());
            p.getJavaVersion().set(launcher.getMetadata().getLanguageVersion().asInt());
            p.getBuildTools().set(buildTools);
            p.getCacheDirectory().set(getCacheDirectory());
            p.getServerJar().set(getServerJar());
        });
    }

    public interface Params extends WorkParameters {
        Property<String> getMinecraftVersion();

        RegularFileProperty getJavaExecutable();

        Property<Integer> getJavaVersion();

        RegularFileProperty getBuildTools();

        DirectoryProperty getCacheDirectory();

        RegularFileProperty getServerJar();
    }

    public abstract static class Work implements WorkAction<Params> {

        private static final Logger LOGGER = Logging.getLogger(BuildSpigotTask.class);
        private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

        @Inject
        protected abstract ExecOperations getExec();

        @Inject
        protected abstract FileSystemOperations getFiles();

        @Override
        public void execute() {
            Params p = getParameters();
            String version = p.getMinecraftVersion().get();
            File serverJar = p.getServerJar().get().getAsFile();
            File cache = p.getCacheDirectory().get().getAsFile();
            // Each version gets its own checkout, so parallel builds don't interfere.
            File workDir = new File(cache, "work/" + version);
            getFiles().delete(spec -> spec.delete(workDir));
            workDir.mkdirs();
            File log = new File(cache, "work/buildtools-" + version + ".log");
            LOGGER.lifecycle("[{}] Building Spigot {} with BuildTools on Java {}. This takes a few minutes; the jar is cached in {}",
                    version, version, p.getJavaVersion().get(), serverJar.getParent());

            long start = System.nanoTime();
            ExecResult result;
            try (OutputStream out = new FileOutputStream(log)) {
                result = getExec().exec(spec -> {
                    spec.setExecutable(p.getJavaExecutable().get().getAsFile());
                    spec.args("-jar", p.getBuildTools().get().getAsFile().getAbsolutePath(),
                            "--rev", version,
                            "--output-dir", serverJar.getParentFile().getAbsolutePath(),
                            "--final-name", serverJar.getName());
                    spec.setWorkingDir(workDir);
                    if (WINDOWS) {
                        // Makes BuildTools use its own PortableGit instead of a "bash" that
                        // Windows may resolve to the WSL launcher (see WindowsPath).
                        spec.environment("PATH", WindowsPath.withoutShells(System.getenv("PATH")));
                    }
                    spec.setStandardOutput(out);
                    spec.setErrorOutput(out);
                    spec.setIgnoreExitValue(true);
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (result.getExitValue() != 0 || !serverJar.isFile()) {
                throw new GradleException("BuildTools failed for Spigot " + version + " (exit code " + result.getExitValue()
                        + "). Last lines of " + log + ":\n" + Logs.tail(log, 30));
            }
            LOGGER.lifecycle("[{}] Built Spigot {} in {} s", version, version, Duration.ofNanos(System.nanoTime() - start).toSeconds());
            // The checkouts are several hundred MB per version and are not needed any more.
            getFiles().delete(spec -> spec.delete(workDir));
        }
    }
}
