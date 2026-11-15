plugins {
    `java-library`
    `maven-publish`
}

// Runs inside the Minecraft server, so it has to work on Java 8 (Spigot 1.8.8 - 1.16.5).
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

dependencies {
    // Compiled against the oldest supported API; only API that exists in every version is used.
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")

    // JUnit 5 is the last JUnit generation that still runs on Java 8.
    api(platform("org.junit:junit-bom:5.14.4"))
    api("org.junit.jupiter:junit-jupiter")
    implementation("org.junit.platform:junit-platform-launcher")
    implementation("org.junit.platform:junit-platform-reporting")
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
