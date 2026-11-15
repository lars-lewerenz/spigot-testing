subprojects {
    group = "io.github.lars-lewerenz"
    // Releases pass -PreleaseVersion=<version> (see .github/workflows/release.yml).
    version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")

    plugins.withId("java") {
        // Every published jar carries the license.
        tasks.named<Jar>("jar") {
            from(rootProject.file("../LICENSE")) { into("META-INF") }
        }
    }

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name = "spigot-testing"
                    description = "Real-server tests for Spigot and Paper plugins, from 1.8.8 to 26.x, with Gradle and JUnit 5"
                    url = "https://github.com/lars-lewerenz/spigot-testing"
                    licenses {
                        license {
                            name = "MIT License"
                            url = "https://github.com/lars-lewerenz/spigot-testing/blob/main/LICENSE"
                        }
                    }
                    scm {
                        url = "https://github.com/lars-lewerenz/spigot-testing"
                        connection = "scm:git:https://github.com/lars-lewerenz/spigot-testing.git"
                    }
                }
            }
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
