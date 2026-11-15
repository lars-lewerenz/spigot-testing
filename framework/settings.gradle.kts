dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/groups/public/")
    }
}

rootProject.name = "spigot-testing-framework"

include("spigot-testing-runtime", "spigot-testing-gradle-plugin")
project(":spigot-testing-runtime").projectDir = file("runtime")
project(":spigot-testing-gradle-plugin").projectDir = file("gradle-plugin")
