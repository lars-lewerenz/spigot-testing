dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/groups/public/")
        maven("https://repo.opencollab.dev/main/") {
            // MCProtocolLib and the libraries it needs that are not on Maven Central
            content { includeGroupByRegex("org\\.geysermc\\..*|org\\.cloudburstmc(\\..*)?|com\\.nukkitx\\..*") }
        }
    }
}

rootProject.name = "spigot-testing-framework"

include("spigot-testing-runtime", "spigot-testing-gradle-plugin", "spigot-testing-bots")
project(":spigot-testing-runtime").projectDir = file("runtime")
project(":spigot-testing-gradle-plugin").projectDir = file("gradle-plugin")
project(":spigot-testing-bots").projectDir = file("bots")
