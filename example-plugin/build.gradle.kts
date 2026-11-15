plugins {
    java
    id("io.github.lars-lewerenz.spigot-testing")
}

group = "io.github.lars-lewerenz"
version = "1.0.0"

dependencies {
    // Compiled against the oldest API so the same jar runs on every version below.
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

spigotTesting {
    // One Spigot version per Java generation: 1.8.8 / 1.12.2 (Java 8), 1.20.4 (Java 17),
    // 1.21.11 (Java 21) and 26.3 (Java 25).
    versions = listOf("1.8.8", "1.12.2", "1.20.4", "1.21.11", "26.3")
}
