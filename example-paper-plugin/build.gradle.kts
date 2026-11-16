// A Paper-only plugin (paper-plugin.yml, Paper API, Brigadier commands), tested on Paper.
plugins {
    java
    id("io.github.lars-lewerenz.spigot-testing")
}

group = "io.github.lars-lewerenz"
version = "1.0.0"

dependencies {
    compileOnly(libs.paper.api)
    // The tests ask ViaVersion which client version a bot plays on.
    "serverTestCompileOnly"(libs.viaversion.api)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

spigotTesting {
    versions = listOf()                      // Paper plugins don't run on Spigot
    paperVersions = listOf("1.21.11", "26.3")
    // ViaVersion with its add-ons lets players on other client versions join, like on many real
    // servers; Bots.join(name, "1.8.8") joins as such a player.
    val via = "https://github.com/ViaVersion"
    pluginUrls.addAll(
        "$via/ViaVersion/releases/download/${libs.versions.viaversion.get()}/ViaVersion-${libs.versions.viaversion.get()}.jar",
        "$via/ViaBackwards/releases/download/${libs.versions.viabackwards.get()}/ViaBackwards-${libs.versions.viabackwards.get()}.jar",
        "$via/ViaRewind/releases/download/${libs.versions.viarewind.get()}/ViaRewind-${libs.versions.viarewind.get()}.jar",
    )
}
