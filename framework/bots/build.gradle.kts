plugins {
    java
}

// The bot controller runs next to the test server as its own process. MCProtocolLib needs Java 21.
tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

dependencies {
    implementation("org.geysermc.mcprotocollib:protocol:26.3-4")
    implementation("net.kyori:adventure-text-serializer-legacy:5.2.0")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
}

// One self-contained jar, embedded in the Gradle plugin, so projects using the plugin
// don't need MCProtocolLib's repository.
val fatJar by tasks.registering(Jar::class) {
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "io.github.larslewerenz.spigottesting.bots.BotController")
    }
    from(sourceSets.main.map { it.output })
    from(configurations.runtimeClasspath.map { files -> files.map { if (it.isDirectory) it else zipTree(it) } })
    exclude("module-info.class", "META-INF/versions/*/module-info.class", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

configurations.consumable("botsJar") {
    outgoing.artifact(fatJar)
}
