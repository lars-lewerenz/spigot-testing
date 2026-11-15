plugins {
    `java-gradle-plugin`
    `maven-publish`
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

gradlePlugin {
    plugins {
        create("spigotTesting") {
            id = "io.github.lars-lewerenz.spigot-testing"
            implementationClass = "io.github.larslewerenz.spigottesting.gradle.SpigotTestingPlugin"
            displayName = "Spigot Testing"
            description = "Unit and real-server integration tests for Spigot plugins across Minecraft versions"
        }
    }
}

// Unit tests and Gradle TestKit tests (java-gradle-plugin wires TestKit and the plugin under test).
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test {
    useJUnitPlatform()
}

// The bot controller (MCProtocolLib + dependencies) as one jar, shipped inside this plugin.
val botsJar = configurations.dependencyScope("botsJar")
val botsJarFiles = configurations.resolvable("botsJarFiles") {
    extendsFrom(botsJar.get())
}
dependencies {
    botsJar(project(":spigot-testing-bots", configuration = "botsJar"))
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString(), "junitVersion" to "5.14.4")
    inputs.properties(props)
    filesMatching("**/spigot-testing.properties") {
        expand(props)
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
