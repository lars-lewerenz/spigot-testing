plugins {
    // Lints all framework code during compilation (https://errorprone.info).
    alias(libs.plugins.errorprone) apply false
}

subprojects {
    group = "io.github.lars-lewerenz"
    // Releases pass -PreleaseVersion=<version> (see .github/workflows/release.yml).
    version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")

    plugins.withId("java") {
        apply(plugin = "net.ltgt.errorprone")
        dependencies {
            "errorprone"(rootProject.libs.errorprone.core)
        }
        tasks.withType<JavaCompile>().configureEach {
            // Lint findings fail the build instead of scrolling by.
            options.compilerArgs.add("-Werror")
        }

        tasks.withType<Javadoc>().configureEach {
            // Broken links, bad HTML and wrong tags fail the build. Not every getter needs a
            // comment or @return, so the "missing" checks are off.
            (options as StandardJavadocDocletOptions).apply {
                addBooleanOption("Xdoclint:all,-missing", true)
                addBooleanOption("Werror", true)
            }
        }

        // Unit tests of the framework's own pieces.
        dependencies {
            "testImplementation"(platform(rootProject.libs.junit.bom))
            "testImplementation"("org.junit.jupiter:junit-jupiter")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

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
