pluginManagement {
    // The framework lives in this repo as an included build, so the example
    // uses the Gradle plugin and the runtime library straight from source.
    includeBuild("framework")
}

plugins {
    // Downloads the JDKs the different Spigot versions need (8, 17, 21, 25).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/groups/public/")
        maven("https://repo.extendedclip.com/releases/") {
            content { includeGroup("me.clip") } // PlaceholderAPI
        }
    }
}

rootProject.name = "spigot-testing"

includeBuild("framework")
include("example-plugin")
