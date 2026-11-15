subprojects {
    group = "io.github.lars-lewerenz"
    // Releases pass -PreleaseVersion=<version> (see .github/workflows/release.yml).
    version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven("https://maven.pkg.github.com/lars-lewerenz/spigot-testing") {
                    name = "GitHubPackages"
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
    }
}
