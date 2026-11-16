plugins {
    java
}

// The bot controller runs next to the test server as its own process. MCProtocolLib needs Java 21.
tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

dependencies {
    implementation(libs.mcprotocollib)
    implementation(libs.adventure.text.serializer.legacy)
    runtimeOnly(libs.slf4j.nop)
}

configurations.runtimeClasspath {
    // Microsoft-account login (LGPL-3.0) and the HTTP/JSON helpers only it uses: bots join
    // offline-mode servers, so none of it is needed or bundled.
    exclude(group = "net.raphimc", module = "MinecraftAuth")
    exclude(group = "net.lenni0451.commons")
}

// One self-contained jar, embedded in the Gradle plugin, so projects using the plugin
// don't need MCProtocolLib's repository. The libraries' licenses are listed in
// META-INF/THIRD-PARTY-NOTICES.txt.
val fatJar = tasks.register<Jar>("fatJar") {
    archiveClassifier = "all"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "io.github.larslewerenz.spigottesting.bots.BotController")
    }
    from(rootProject.file("../LICENSE")) { into("META-INF") }
    from(sourceSets.main.map { it.output })
    // Netty's NOTICE and the licenses of the third-party code it contains (Apache-2.0 asks for them).
    from(configurations.runtimeClasspath.map { files ->
        files.filter { it.name.startsWith("netty-codec-native-quic") }.map { zipTree(it) }
    }) {
        include("META-INF/NOTICE.txt", "META-INF/license/**")
        eachFile { path = "META-INF/licenses/netty/" + path.substringAfterLast('/') }
        includeEmptyDirs = false
    }
    from(configurations.runtimeClasspath.map { files -> files.map { if (it.isDirectory) it else zipTree(it) } }) {
        // Licenses are collected above; don't let one library's file stand in for all of them.
        exclude("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/license/**", "LICENSE*")
    }
    exclude("module-info.class", "META-INF/versions/*/module-info.class", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

configurations.consumable("botsJar") {
    outgoing.artifact(fatJar)
}
