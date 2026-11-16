plugins {
    java
    id("io.github.lars-lewerenz.spigot-testing")
}
dependencies {
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
}
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}
spigotTesting {
    paperVersions = listOf("26.3")
}
