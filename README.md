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

Add `--continue` to see the results of every version even when one of them fails.

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
- `ServerTest.recordEvents(SomeEvent.class)` records events as they look after every plugin has
  handled them (MONITOR priority, cancelled ones included): `events()`, or `awaitEvent(...)` in
  `@OffMainThread` tests. Recording stops at the end of the test.

  ```java
  EventRecorder<CreatureSpawnEvent> spawns = ServerTest.recordEvents(CreatureSpawnEvent.class);
  world.spawnEntity(location, EntityType.CREEPER);
  assertTrue(spawns.events().get(0).isCancelled());
  ```
- The tests are loaded by a different class loader than your plugin, so they can only use your
  plugin's **public** API, even from the same package.
- Compile the tests for the oldest Java you target (`options.release = 8` for 1.8.8 – 1.16.5).

## Fake players (bots)

`Bots.join(name)` connects a bot client to the test server over the real network protocol. The
server goes through its normal login, so your plugin sees a real player: `PlayerLoginEvent`,
`PlayerJoinEvent`, permissions, commands, chat, inventory, teleports, quitting.

```java
@OffMainThread // bots wait for the server, so it has to keep ticking
class GreeterPlayerTest {

    @Test
    void joiningPlayersAreWelcomed() {
        Bot alex = Bots.join("Alex");

        assertEquals(ChatColor.YELLOW + "Welcome, Alex!", alex.awaitMessage("Welcome"));
    }

    @Test
    void chatReachesOtherPlayers() {
        Bot alex = Bots.join("Alex");
        Bot bob = Bots.join("Bob");

        alex.chat("hi bob"); // "/..." is sent as a command

        assertTrue(bob.awaitMessage("hi bob").contains("Alex"));
    }
}
```

- `bot.player()` is the server-side Bukkit `Player`; use it inside `ServerTest.sync(...)`.
- `bot.chat(text)` sends chat or a command (`/greet Steve`) from the client.
- `bot.messages()` / `bot.awaitMessage(...)` show what the client received, with `§` color codes.
- `bot.quit()` disconnects and waits until the server has removed the player. Bots still online
  at the end of a test are disconnected automatically.

### Inventory GUIs

Bots see the inventories the server opens for them and click them with real click packets, so
your plugin gets the same `InventoryClickEvent`s as from a player:

```java
@Test
void clickingThePaperGreetsAndClosesTheMenu() {
    Bot alex = Bots.join("Alex");
    alex.chat("/menu");
    assertEquals(GreeterMenu.TITLE, alex.awaitInventory("Greeter Menu"));

    alex.click(GreeterMenu.GREET_SLOT); // or click(slot, ClickType.SHIFT_LEFT)

    assertEquals(ChatColor.GREEN + "Hello, Alex!", alex.awaitMessage("Hello"));
}
```

- `bot.awaitInventory(title)` waits until the client has an inventory with that title open;
  `bot.openInventoryTitle()` returns the current one (or null).
- `bot.click(slot)` / `bot.click(slot, ClickType)`: `LEFT`, `RIGHT`, `SHIFT_LEFT` or `SHIFT_RIGHT`
  on a raw slot number (top inventory first, then the player's), like
  `InventoryClickEvent#getRawSlot`.
- `bot.closeInventory()` closes it like pressing Escape.
- Check the items through the server side, e.g. `bot.player().getOpenInventory()` inside
  `ServerTest.sync(...)`, and the clicks with `ServerTest.recordEvents(InventoryClickEvent.class)`.

## Other plugins on the test server

Install the plugins yours works with (Vault, PlaceholderAPI, LuckPerms, …) next to it:

```kotlin
dependencies {
    serverTestPlugin(files("libs/SomePlugin.jar"))          // local jar
    serverTestPlugin("com.example:some-plugin:1.0")         // runnable plugin jar from Maven
}
spigotTesting {
    pluginUrls.add("https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar")
}
```

- `serverTestPlugin` jars are also on the server tests' compile classpath.
- `pluginUrls` are downloaded once and cached in the cache directory.
- The test harness soft-depends on all of them, so they are enabled before the tests run and the
  tests can call their APIs (e.g. `PlaceholderAPI.setPlaceholders(...)`).
- Many plugins publish only their **API** on Maven (PlaceholderAPI does: the Maven jar lacks the
  libraries the real plugin bundles and won't load). Compile against that, and install the real
  plugin jar from its release download.
- The same plugins are installed on every version, so pick plugin versions that support your
  whole version range.

The bots use [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib), which speaks the newest
protocol, and connect through [ViaProxy](https://github.com/ViaVersion/ViaProxy), which
translates to the server's version, so the same bots work from 1.8.8 to 26.x. Both run in a
separate process next to the server (on Java 21, provisioned like the other JDKs); ViaProxy is
downloaded once into the cache directory and only started when a test uses bots.

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
    pluginUrls.add("https://…/OtherPlugin.jar") // other plugins for the test servers
    jvmArgs = listOf("-Xmx2G")                  // default
    timeout = java.time.Duration.ofMinutes(10)  // per server run, default 10 minutes
    acceptEula = true                           // default: Gradle property spigotTesting.acceptEula
    cacheDirectory = layout.projectDirectory.dir(".spigot") // default: ~/.gradle/caches/spigot-testing
}
```

## Repository layout

- `framework/` — included Gradle build with the framework itself
  - `gradle-plugin/` — the Gradle plugin `io.github.lars-lewerenz.spigot-testing` (Java 17+)
  - `runtime/` — the code that runs inside the server: `ServerTest`, `@OffMainThread`, `Bots`,
    the JUnit runner (Java 8)
  - `bots/` — the bot controller (MCProtocolLib, Java 21), bundled into the Gradle plugin
- `example-plugin/` — a small example plugin (`/greet`, a `/menu` chest GUI, welcomes players,
  blocks creepers, skips the night, a PlaceholderAPI placeholder) with unit tests, server tests
  and tests with bots

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

- Bots can join, chat, run commands, click inventory GUIs and quit, and they record the chat and
  inventory titles they receive. They don't move or interact with blocks and entities from the
  client side yet; do that through the server-side `Player` instead.
- Bots join in offline mode with unsigned chat (the test servers run with `online-mode=false`).
- Spigot only (built with BuildTools). Paper and other forks are not supported yet.
