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

tasks.processResources {
    val props = mapOf("version" to project.version.toString(), "junitVersion" to "5.14.4")
    inputs.properties(props)
    filesMatching("**/spigot-testing.properties") {
        expand(props)
    }
}
