package io.github.larslewerenz.spigottesting.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmVendorSpec;

/**
 * Sets up two kinds of tests for a Spigot plugin project:
 *
 * <ul>
 *   <li><b>Unit tests</b> in {@code src/test}: plain JUnit 5 on the build JVM, with the plugin's
 *       {@code compileOnly} dependencies (the Spigot API) on the test classpath.
 *   <li><b>Server tests</b> in {@code src/serverTest}: JUnit 5 tests that run inside a real
 *       Spigot server, once per configured version ({@code serverTest_<version>} tasks).
 * </ul>
 */
public abstract class SpigotTestingPlugin implements Plugin<Project> {

    public static final String SOURCE_SET_NAME = "serverTest";
    public static final String PLUGIN_CONFIGURATION_NAME = "serverTestPlugin";
    private static final String GROUP = "verification";
    /** MCProtocolLib, which the bot controller uses, needs Java 21. */
    private static final int BOT_JAVA_VERSION = 21;
    /** Translates the bots' protocol (the newest version) to the version of each test server. */
    private static final String VIAPROXY_VERSION = "3.4.14";
    private static final boolean APPLE_SILICON = System.getProperty("os.name").startsWith("Mac")
            && System.getProperty("os.arch").equals("aarch64");

    @Inject
    protected abstract JavaToolchainService getToolchains();

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        Properties versions = loadVersions();

        SpigotTestingExtension extension = project.getExtensions().create("spigotTesting", SpigotTestingExtension.class);
        extension.getAcceptEula().convention(project.getProviders().gradleProperty("spigotTesting.acceptEula")
                .map(Boolean::parseBoolean).orElse(false));
        extension.getJvmArgs().convention(List.of("-Xmx2G"));
        extension.getTimeout().convention(Duration.ofMinutes(10));
        extension.getPluginJar().convention(project.getTasks().named("jar", Jar.class).flatMap(Jar::getArchiveFile));
        extension.getCacheDirectory().convention(project.getLayout().dir(project.provider(() ->
                new File(project.getGradle().getGradleUserHomeDir(), "caches/spigot-testing"))));

        configureUnitTests(project, versions.getProperty("junitVersion"));
        SourceSet serverTest = createServerTestSourceSet(project);
        project.getDependencies().add(serverTest.getImplementationConfigurationName(),
                "io.github.lars-lewerenz:spigot-testing-runtime:" + versions.getProperty("version"));

        FileCollection otherPlugins = configureOtherPlugins(project, extension, serverTest);
        TaskProvider<Jar> harnessJar = registerHarnessJar(project, extension, serverTest, otherPlugins);
        TaskProvider<ExtractBotController> botController = project.getTasks().register(
                "extractBotController", ExtractBotController.class, task ->
                        task.getJar().set(project.getLayout().getBuildDirectory().file("spigot-testing/spigot-testing-bots.jar")));
        TaskProvider<DownloadViaProxy> viaProxy = project.getTasks().register("downloadViaProxy", DownloadViaProxy.class, task -> {
            task.getUrl().set("https://github.com/ViaVersion/ViaProxy/releases/download/v"
                    + VIAPROXY_VERSION + "/ViaProxy-" + VIAPROXY_VERSION + ".jar");
            task.getJar().set(extension.getCacheDirectory().map(dir -> dir.file("viaproxy/ViaProxy-" + VIAPROXY_VERSION + ".jar")));
        });

        TaskProvider<CheckEula> checkEula = project.getTasks().register("checkMinecraftEula", CheckEula.class, task -> {
            task.setDescription("Fails unless the Minecraft EULA has been accepted (spigotTesting.acceptEula).");
            task.getAcceptEula().set(extension.getAcceptEula());
        });

        TaskProvider<Task> allServerTests = project.getTasks().register("serverTest", task -> {
            task.setGroup(GROUP);
            task.setDescription("Runs the server tests on every configured Spigot and Paper version.");
        });
        project.getTasks().named("check").configure(task -> task.dependsOn(allServerTests));

        // One task pair per server, so the version lists have to be known.
        extension.getPaperVersions().convention(List.of());
        project.afterEvaluate(p -> {
            for (String version : versionsFor(project, "spigotTesting.versions", extension.getVersions().get())) {
                TaskProvider<ServerTestTask> task = registerVersion(project, extension, harnessJar, otherPlugins,
                        botController, viaProxy, checkEula, ServerType.SPIGOT, version);
                allServerTests.configure(t -> t.dependsOn(task));
            }
            for (String version : versionsFor(project, "spigotTesting.paperVersions", extension.getPaperVersions().get())) {
                TaskProvider<ServerTestTask> task = registerVersion(project, extension, harnessJar, otherPlugins,
                        botController, viaProxy, checkEula, ServerType.PAPER, version);
                allServerTests.configure(t -> t.dependsOn(task));
            }
        });
    }

    /**
     * The versions from the build script, unless overridden on the command line, e.g.
     * {@code -PspigotTesting.versions=1.8.8,1.21.11} (an empty value means none).
     */
    private static List<String> versionsFor(Project project, String property, List<String> configured) {
        String override = project.getProviders().gradleProperty(property).getOrNull();
        if (override == null) {
            return configured;
        }
        return java.util.Arrays.stream(override.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toList());
    }

    private static void configureUnitTests(Project project, String junitVersion) {
        ConfigurationContainer configurations = project.getConfigurations();
        // compileOnly (where the Spigot API usually lives) is not part of the test classpath by default.
        Configuration compileOnly = configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME);
        configurations.getByName(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(compileOnly);
        configurations.getByName(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME).extendsFrom(compileOnly);

        project.getDependencies().add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME,
                project.getDependencies().platform("org.junit:junit-bom:" + junitVersion));
        project.getDependencies().add(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, "org.junit.jupiter:junit-jupiter");
        project.getDependencies().add(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME, "org.junit.platform:junit-platform-launcher");
        project.getTasks().named(JavaPlugin.TEST_TASK_NAME, Test.class).configure(Test::useJUnitPlatform);
    }

    private static SourceSet createServerTestSourceSet(Project project) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet serverTest = sourceSets.create(SOURCE_SET_NAME);
        // Compile against the plugin and its API. At runtime both come from the server, so they
        // stay out of the runtime classpath (and out of the harness jar).
        serverTest.setCompileClasspath(serverTest.getCompileClasspath()
                .plus(main.getOutput())
                .plus(project.getConfigurations().getByName(main.getCompileClasspathConfigurationName())));
        return serverTest;
    }

    /**
     * Other plugins for the test servers: the {@code serverTestPlugin} configuration (Maven
     * coordinates or files) plus {@code spigotTesting.pluginUrls}. They are also on the server
     * tests' compile classpath, so tests can use their APIs.
     */
    private static FileCollection configureOtherPlugins(Project project, SpigotTestingExtension extension, SourceSet serverTest) {
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration declared = configurations.dependencyScope(PLUGIN_CONFIGURATION_NAME, c ->
                c.setDescription("Other plugins to install on the test servers, next to the plugin under test.")).get();
        Configuration files = configurations.resolvable(PLUGIN_CONFIGURATION_NAME + "Files", c -> {
            c.extendsFrom(declared);
            c.setTransitive(false); // plugin jars bundle what they need
        }).get();
        configurations.getByName(serverTest.getCompileOnlyConfigurationName()).extendsFrom(declared);

        extension.getPluginUrls().convention(List.of());
        TaskProvider<DownloadPlugins> downloads = project.getTasks().register("downloadServerTestPlugins", DownloadPlugins.class, task -> {
            task.getUrls().set(extension.getPluginUrls());
            task.getCacheDirectory().set(extension.getCacheDirectory().map(dir -> dir.dir("plugins")));
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("spigot-testing/downloaded-plugins"));
        });
        return project.files(files, project.files(downloads.flatMap(DownloadPlugins::getOutputDirectory)).getAsFileTree());
    }

    private static TaskProvider<Jar> registerHarnessJar(Project project, SpigotTestingExtension extension, SourceSet serverTest,
            FileCollection otherPlugins) {
        TaskProvider<GenerateHarnessResources> resources = project.getTasks().register(
                "generateServerTestHarnessResources", GenerateHarnessResources.class, task -> {
                    task.getPluginJar().set(extension.getPluginJar());
                    task.getOtherPlugins().from(otherPlugins);
                    task.getTestClassesDirs().from(serverTest.getOutput().getClassesDirs());
                    task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated/spigot-testing"));
                });

        Configuration runtimeClasspath = project.getConfigurations().getByName(serverTest.getRuntimeClasspathConfigurationName());
        return project.getTasks().register("serverTestHarnessJar", Jar.class, jar -> {
            jar.setDescription("Packages the server tests, JUnit and the test runtime as a Spigot plugin.");
            jar.getArchiveFileName().set("spigot-testing-harness.jar");
            jar.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("spigot-testing"));
            jar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            jar.from(resources);
            jar.from(serverTest.getOutput());
            jar.dependsOn(runtimeClasspath);
            jar.from(runtimeClasspath.getElements().map(files -> files.stream()
                    .map(f -> f.getAsFile().isDirectory() ? f.getAsFile() : project.zipTree(f.getAsFile()))
                    .collect(Collectors.toList())));
            jar.exclude("module-info.class", "META-INF/versions/*/module-info.class",
                    "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA");
        });
    }

    private TaskProvider<ServerTestTask> registerVersion(Project project, SpigotTestingExtension extension,
            TaskProvider<Jar> harnessJar, FileCollection otherPlugins, TaskProvider<ExtractBotController> botController,
            TaskProvider<DownloadViaProxy> viaProxy, TaskProvider<CheckEula> checkEula, ServerType type, String version) {
        String id = type.taskPrefix + version.replaceAll("[^A-Za-z0-9]", "_");
        String name = type.displayName + " " + version;
        String dir = type.directoryPrefix + version;
        Provider<Directory> cache = extension.getCacheDirectory();
        Provider<JavaLauncher> launcher = cache.flatMap(cacheDir -> {
            int java = type == ServerType.PAPER
                    ? PaperVersions.requiredJava(version, cacheDir.getAsFile())
                    : SpigotVersions.requiredJava(version, cacheDir.getAsFile());
            return getToolchains().launcherFor(spec -> {
                spec.getLanguageVersion().set(JavaLanguageVersion.of(java));
                if (java == 8 && APPLE_SILICON) {
                    // Most vendors only ship Java 8 for x64 macOS, which would run under Rosetta.
                    spec.getVendor().set(JvmVendorSpec.AZUL);
                }
            });
        });

        Provider<RegularFile> serverJar;
        if (type == ServerType.PAPER) {
            serverJar = project.getTasks().register("downloadPaper_" + id.substring(type.taskPrefix.length()), DownloadPaperTask.class, task -> {
                task.setGroup("build setup");
                task.setDescription("Downloads the latest Paper " + version + " build (cached).");
                task.getMinecraftVersion().set(version);
                task.getCacheDirectory().set(cache);
                task.getServerJar().set(cache.map(c -> c.file("servers/paper-" + version + ".jar")));
                task.mustRunAfter(checkEula);
            }).flatMap(DownloadPaperTask::getServerJar);
        } else {
            serverJar = project.getTasks().register("buildSpigot_" + id, BuildSpigotTask.class, task -> {
                task.setGroup("build setup");
                task.setDescription("Builds Spigot " + version + " with BuildTools (cached).");
                task.getMinecraftVersion().set(version);
                task.getJavaLauncher().set(launcher);
                task.getCacheDirectory().set(cache);
                task.getServerJar().set(cache.map(c -> c.file("servers/spigot-" + version + ".jar")));
                task.mustRunAfter(checkEula);
            }).flatMap(BuildSpigotTask::getServerJar);
        }

        return project.getTasks().register("serverTest_" + id, ServerTestTask.class, task -> {
            task.setGroup(GROUP);
            task.setDescription("Runs the server tests on " + name + ".");
            task.dependsOn(checkEula);
            task.getMinecraftVersion().set(version);
            task.getServerName().set(name);
            task.getServerJar().set(serverJar);
            task.getPluginJar().set(extension.getPluginJar());
            task.getHarnessJar().set(harnessJar.flatMap(Jar::getArchiveFile));
            task.getOtherPlugins().from(otherPlugins);
            task.getJavaLauncher().set(launcher);
            task.getBotControllerJar().set(botController.flatMap(ExtractBotController::getJar));
            task.getBotJavaLauncher().set(getToolchains().launcherFor(spec ->
                    spec.getLanguageVersion().set(JavaLanguageVersion.of(BOT_JAVA_VERSION))));
            task.getViaProxyJar().set(viaProxy.flatMap(DownloadViaProxy::getJar));
            task.getAcceptEula().set(extension.getAcceptEula());
            task.getJvmArgs().set(extension.getJvmArgs());
            task.getRunTimeout().set(extension.getTimeout());
            task.getServerDirectory().set(project.getLayout().getBuildDirectory().dir("spigot-testing/" + dir + "/server"));
            task.getResultsDirectory().set(project.getLayout().getBuildDirectory().dir("spigot-testing/" + dir + "/results"));
        });
    }

    /** Spigot (built with BuildTools) or Paper (downloaded from PaperMC). */
    enum ServerType {
        SPIGOT("Spigot", "", ""),
        PAPER("Paper", "paper_", "paper-");

        final String displayName;
        final String taskPrefix;
        final String directoryPrefix;

        ServerType(String displayName, String taskPrefix, String directoryPrefix) {
            this.displayName = displayName;
            this.taskPrefix = taskPrefix;
            this.directoryPrefix = directoryPrefix;
        }
    }

    private static Properties loadVersions() {
        Properties props = new Properties();
        try (InputStream in = SpigotTestingPlugin.class.getResourceAsStream("spigot-testing.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return props;
    }
}
