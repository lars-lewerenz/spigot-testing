dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/groups/public/")
        // "maven-releases" rather than the "main" group: from GitHub Actions, the CDN in front of
        // "main" answered 404 for text-serializer-nbt-0.2.1.jar although the file exists.
        maven("https://repo.opencollab.dev/maven-releases/") {
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
