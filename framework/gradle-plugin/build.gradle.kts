import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-gradle-plugin`
    `maven-publish`
    // Publishes to the Gradle Plugin Portal (see .github/workflows/release.yml).
    alias(libs.plugins.plugin.publish)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    // Gradle injects its services into abstract @Inject getters (its documented pattern); Error
    // Prone's javax.inject check assumes a plain DI container and flags every one of them.
    options.errorprone.disable("JavaxInjectOnAbstractMethod")
}

gradlePlugin {
    website = "https://github.com/lars-lewerenz/spigot-testing"
    vcsUrl = "https://github.com/lars-lewerenz/spigot-testing"
    plugins {
        create("spigotTesting") {
            id = "io.github.lars-lewerenz.spigot-testing"
            implementationClass = "io.github.larslewerenz.spigottesting.gradle.SpigotTestingPlugin"
            displayName = "spigot-testing"
            description = "Real-server tests for Spigot and Paper plugins, from 1.8.8 to 26.x, with JUnit 5: " +
                "commands, events, fake players over the real network protocol, inventory GUIs and other plugins, " +
                "on every Minecraft version you support."
            tags = listOf("minecraft", "spigot", "paper", "bukkit", "testing", "integration-testing", "junit")
        }
    }
}

// Unit tests and Gradle TestKit tests (JUnit is set up in ../build.gradle.kts; java-gradle-plugin
// wires TestKit and the plugin under test).
tasks.test {
    // TestKit runs the tested Gradle versions on this JVM; Gradle 8.x can't run on newer Java.
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
}

// Jars shipped inside this plugin, so projects need no repository besides the Plugin Portal and
// Maven Central: the test runtime (added to the server tests) and the bot controller
// (MCProtocolLib + dependencies, started next to the test servers).
val bundledRuntime = configurations.dependencyScope("bundledRuntime")
val bundledBots = configurations.dependencyScope("bundledBots")
val runtimeJarFiles = configurations.resolvable("runtimeJarFiles") {
    extendsFrom(bundledRuntime.get())
    isTransitive = false // its JUnit dependencies are added from Maven Central instead
}
val botsJarFiles = configurations.resolvable("botsJarFiles") {
    extendsFrom(bundledBots.get())
}
dependencies {
    bundledRuntime(project(":spigot-testing-runtime", configuration = "runtimeElements"))
    bundledBots(project(":spigot-testing-bots", configuration = "botsJar"))
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "junitVersion" to libs.versions.junit.get(),
        "viaproxyVersion" to libs.versions.viaproxy.get(),
    )
    inputs.properties(props)
    filesMatching("**/spigot-testing.properties") {
        expand(props)
    }
    from(runtimeJarFiles) {
        into("io/github/larslewerenz/spigottesting/gradle")
        rename { "spigot-testing-runtime.jar" }
    }
    from(botsJarFiles) {
        into("io/github/larslewerenz/spigottesting/gradle")
        rename { "spigot-testing-bots.jar" }
    }
    // The bundled bot controller's third-party licenses, also at the top level of this jar.
    from(project(":spigot-testing-bots").file("src/main/resources/META-INF/THIRD-PARTY-NOTICES.txt")) {
        into("META-INF")
    }
}
