// A Paper-only plugin (paper-plugin.yml, Paper API, Brigadier commands), tested on Paper.
plugins {
    java
    id("io.github.lars-lewerenz.spigot-testing")
}

group = "io.github.lars-lewerenz"
version = "1.0.0"

dependencies {
    compileOnly(libs.paper.api)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

spigotTesting {
    versions = listOf()                      // Paper plugins don't run on Spigot
    paperVersions = listOf("1.21.11", "26.3")
}
