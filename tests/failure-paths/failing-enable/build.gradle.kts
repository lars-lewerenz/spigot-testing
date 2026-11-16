plugins {
    java
    id("io.github.lars-lewerenz.spigot-testing")
}
dependencies {
    compileOnly(libs.spigot.api)
}
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}
spigotTesting {
    paperVersions = listOf("26.3")
}
