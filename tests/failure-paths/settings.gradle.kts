// Projects that are supposed to fail, to check that failures are reported properly.
// Run with ./check.sh (see there).
pluginManagement {
    includeBuild("../../framework")
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/groups/public/")
    }
}
rootProject.name = "failure-paths"
include("failing-test", "broken-plugin", "failing-enable")
