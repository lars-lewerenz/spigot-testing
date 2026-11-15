# spigot-testing

Unit and integration tests for Spigot plugins, run against **real Spigot servers** of as many
versions as you like — from 1.8.8 to the latest — with Gradle and JUnit 5.

```kotlin
plugins {
    java
    id("io.github.lars-lewerenz.spigot-testing")
}

spigotTesting {
    versions = listOf("1.8.8", "1.12.2", "1.20.4", "1.21.11", "26.3")
}
```

```
./gradlew test                 # unit tests (src/test), plain JVM, seconds
./gradlew serverTest           # server tests (src/serverTest) on every version
./gradlew serverTest_1_8_8     # ... or on one version
./gradlew check                # both
```

## Two kinds of tests

| | Unit tests | Server tests |
|---|---|---|
| Sources | `src/test/java` | `src/serverTest/java` |
| Runs on | the build JVM | a real Spigot server per version |
| Bukkit API | on the classpath, but no server (`Bukkit.getServer()` is `null`) | fully working |
| Good for | logic you can keep free of the running server | commands, events, the scheduler, worlds, entities |

Both use JUnit 5 (Jupiter). The plugin adds JUnit and puts your `compileOnly` dependencies (the
Spigot API) on the unit test classpath, so you can use API classes like `ChatColor` in unit tests.

## Writing server tests

```java
class GreeterServerTest {

    private final GreeterPlugin plugin = JavaPlugin.getPlugin(GreeterPlugin.class);

    @Test // runs on the server main thread: use the Bukkit API directly
    void greetCommandGreetsByName() {
        CommandResult result = ServerTest.dispatchCommand("greet Steve");

        assertTrue(result.success());
        assertEquals(singletonList(ChatColor.GREEN + "Hello, Steve!"), result.messages());
    }

    @Test
    @OffMainThread // runs on the test thread, so the server can keep ticking
    void nightIsSkipped() {
        World world = ServerTest.sync(() -> Bukkit.getWorlds().get(0));
        ServerTest.sync(() -> world.setTime(18000));

        ServerTest.awaitTicks(40);

        assertTrue(ServerTest.sync(world::getTime) < 12000);
    }
}
```

- Test methods (and `@BeforeEach` etc.) run **on the server main thread** by default.
- `@OffMainThread` (method or class) runs them on the test thread instead. Then use
  `ServerTest.sync(...)` for Bukkit calls and `ServerTest.awaitTicks(n)` to let the server tick.
- `ServerTest.dispatchCommand("cmd args")` runs a command as the console and records the messages
  sent back.
- The tests are loaded by a different class loader than your plugin, so they can only use your
  plugin's **public** API, even from the same package.
- Compile the tests for the oldest Java you target (`options.release = 8` for 1.8.8 – 1.16.5).

## How it works

For each version, `serverTest_<version>`:

1. **Builds the server** with [BuildTools](https://www.spigotmc.org/wiki/buildtools/) (Spigot
   jars cannot be downloaded) on the Java version that Spigot version needs, read from
   `hub.spigotmc.org/versions/<version>.json`. JDKs come from Gradle toolchains (auto-downloaded via
   the foojay resolver). The jar is cached in `~/.gradle/caches/spigot-testing/servers`, so this
   happens once per version and machine. BuildTools needs `git` on the `PATH`.
2. **Packages a harness plugin**: your `src/serverTest` classes, the test runtime and JUnit, with a
   generated `plugin.yml` that depends on your plugin.
3. **Starts the server** in `build/spigot-testing/<version>/server` (flat world, offline mode,
   random port on 127.0.0.1) with your plugin jar and the harness.
4. On the first tick the harness runs the tests with the JUnit Platform launcher, writes the
   results and stops the server. Test results are shown in the build output; JUnit XML reports and
   the server log land in `build/spigot-testing/<version>/results`.

## Minecraft EULA

Running a Minecraft server requires accepting the [Minecraft EULA](https://aka.ms/MinecraftEULA).
The server tests refuse to start until you do, by setting the Gradle property
`spigotTesting.acceptEula=true` (on the command line with `-PspigotTesting.acceptEula=true`, or in
`~/.gradle/gradle.properties`).

## Configuration

```kotlin
spigotTesting {
    versions = listOf("1.8.8", "1.21.11")       // required for server tests
    pluginJar = tasks.shadowJar.flatMap { it.archiveFile } // default: the jar task's output
    jvmArgs = listOf("-Xmx2G")                  // default
    timeout = Duration.ofMinutes(10)            // per server run, default 10 minutes
    acceptEula = true                           // default: Gradle property spigotTesting.acceptEula
    cacheDirectory = layout.projectDirectory.dir(".spigot") // default: ~/.gradle/caches/spigot-testing
}
```

## Repository layout

- `framework/` — included Gradle build with the framework itself
  - `gradle-plugin/` — the Gradle plugin `io.github.lars-lewerenz.spigot-testing` (Java 17+)
  - `runtime/` — the code that runs inside the server: `ServerTest`, `@OffMainThread`, the JUnit
    runner (Java 8)
- `example-plugin/` — a small example plugin (`/greet`, blocks creepers, skips the night) with
  unit tests and server tests

## Using it in another project

Until it is published somewhere, install it into your local Maven repository:

```
./gradlew -p framework publishToMavenLocal
```

and in the plugin project:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories { mavenLocal(); gradlePluginPortal() }
}

// build.gradle.kts
plugins { id("io.github.lars-lewerenz.spigot-testing") version "0.1.0-SNAPSHOT" }
repositories {
    mavenLocal()
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
}
```

plus the foojay toolchain resolver in `settings.gradle.kts` (see this repo's), so Gradle can
download the JDKs the servers need.

## Limitations

- No players: there is no fake player or client, so things that need a connected player can only
  be tested through the API around them (commands as console, calling events, worlds, entities).
- Spigot only (built with BuildTools). Paper and other forks are not supported yet.
