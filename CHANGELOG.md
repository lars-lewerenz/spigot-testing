# Changelog

## [Unreleased]

### New

- **What players see besides chat:** `bot.view()` and `bot.awaitView(v -> …)` return a
  `ClientView` with the title and subtitle, the action bar, boss bars, the sidebar scoreboard (as
  the client renders it, team prefixes included) and the tab list with its header and footer.
  Works on every version, whichever API the plugin uses.
- **Restarts:** tests annotated `@AfterRestart` run after the server has been stopped and started
  again with the same folder, to check what a plugin keeps across a restart.

### Fixes

- With many servers in parallel, a bot could join the wrong server: the port picked for one run's
  ViaProxy could be handed to another run's server before ViaProxy started. Ports now stay
  reserved for the whole run, and ViaProxy moves to another port if its own was taken.

## [0.5.0]

### New

- **Tests for some servers only:** `@EnabledOnServer(ServerType.PAPER)` and
  `@EnabledForMinecraftRange(min = "1.13", max = "1.16.5")` (either end may be left out) skip a
  test or class on the other servers. `ServerTest.isMinecraftAtLeast("1.13")` and
  `MinecraftVersion` compare versions in code.
- **Rejected logins and kicks:** `Bots.expectRejection(name)` returns the reason the server gave
  for turning a bot away (bans, the whitelist, a plugin disallowing the login) and fails if the bot
  gets in; `bot.awaitKick()` waits for the server to disconnect a bot and returns the reason.
- **Java per server:** `javaVersions.put("1.8.8", 17)` runs a server on another Java than the
  oldest LTS its version supports. BuildTools still builds Spigot with the Java it needs.

### API changes

- `ServerTest.serverType()` returns `ServerType.SPIGOT` or `ServerType.PAPER` instead of a string.
- `EventRecorder.closeAll()` and `ConfigChange.restoreAll()` are no longer public; they were only
  meant for the framework's own cleanup after each test. The `internal` package is left out of the
  Javadoc and may change in any release.

### Quality

- Unit tests for the runtime and the bot client (version comparison, chat rendering).
- Javadoc is checked strictly (broken links, HTML, tags) and warnings fail the build.

## [0.4.1]

### Fixes

- **Several plugin projects in one build** (e.g. a multi-module project) failed with "Property has
  implicit dependency": each project's tasks claimed the shared server cache as their output. The
  cache is now written under a lock (between projects, and between Gradle processes), so each
  server jar, Paper download and ViaProxy is fetched once and never by two builds at the same time.

### Releases

- Releases are only published after CI passed on the released commit, and the jars come with a
  signed build provenance attestation (`gh attestation verify`).
- Dependencies are kept up to date by Dependabot (version catalog, GitHub Actions pinned to
  commit SHAs); all 36 servers are tested every week.

## [0.4.0]

### Compatibility

- **Gradle configuration cache** supported (and tested).
- **Gradle 8.5+**: tested on 8.5, 8.14, 9.0 and 9.8.
- **Windows**: the framework is built and tested on Windows in CI, with server tests on Spigot 1.8.8
  and Paper 26.3. BuildTools no longer runs into the WSL launcher (System32\bash.exe): it now always
  uses its own PortableGit on Windows.
- **Paper plugins** (`paper-plugin.yml`): the test harness becomes a Paper plugin that can use the
  plugin's classes. New `example-paper-plugin` with a Brigadier command and Adventure messages.

### Failures are reported, everywhere

- Tests no longer run when the plugin under test failed to load: modern Paper loaded the harness
  anyway, which could give a green run without the plugin.
- A plugin whose `onEnable` throws fails the run on every server (Spigot keeps such a plugin
  enabled).
- `tests/failure-paths`: projects that must fail, checked on Spigot and Paper in CI.

Tested on all 36 servers (18 versions, Spigot and Paper).

## [0.3.0]

### Installation without credentials

- Published to the **Gradle Plugin Portal**: `plugins { id("io.github.lars-lewerenz.spigot-testing") version "0.3.0" }`
  works without tokens or extra repositories.
- The test runtime now ships inside the Gradle plugin (like the bot client); its JUnit
  dependencies come from Maven Central. GitHub Packages is no longer needed.

### Fixes

- A rare race in ViaProxy's login handling could fail a bot's join ("An unhandled error occurred
  in your connection"); such joins are now retried. Rejections by the server are not.

Tested on all 36 servers (18 versions, Spigot and Paper), and from a fresh project that has no
GitHub repository configured.

## [0.2.0]

### Server files & configs

- Everything in `src/serverTest/server/` is copied into each test server before it starts:
  whole plugin folders, custom files, subfolders, binary data.
- Layers on top for one server type or version: `server-paper/`, `server-<version>/`,
  `server-paper-<version>/` (later layers win).
- `server.properties` from the layers is merged into the framework's settings; `server-port`,
  `server-ip` and `online-mode` stay under the framework's control.
- `ServerTest.setConfig(plugin, path, value)` changes a config value for one test and restores
  it afterwards.
- `ServerTest.serverType()` and `ServerTest.minecraftVersion()` tell tests where they run.

Tested on all 36 servers (18 versions, Spigot and Paper).

## [0.1.1]

### Licensing

- spigot-testing is now released under the **MIT License**; the published POMs say so and every
  jar carries the license.
- The bundled bot client no longer includes MinecraftAuth (LGPL-3.0, Microsoft-account login)
  or its helper libraries: bots join offline-mode servers and never needed them. The plugin jar
  is ~250 KB smaller.
- The licenses of all bundled libraries (MCProtocolLib, Adventure, Netty, …) now ship in the
  plugin jar as `META-INF/THIRD-PARTY-NOTICES.txt`, with Netty's NOTICE and license files.

Re-tested on all 36 servers (18 versions, Spigot and Paper).

## [0.1.0]

First release.

### Servers

- Server tests on **Spigot** (built with BuildTools) and **Paper** (downloaded from PaperMC,
  SHA-256 checked), one Gradle task per server, all running in parallel.
- Tested on every major version from **1.8.8 to 26.3** on both: 36 servers.
- Each server runs on the right Java (8, 17, 21 or 25 — the lowest LTS release it supports),
  provisioned by Gradle toolchains.
- Server jars, JDKs and downloads are cached per machine.
- `-PspigotTesting.versions=…` / `-PspigotTesting.paperVersions=…` pick servers for one run.

### Tests

- Unit tests (`src/test`) with JUnit 5 and the Spigot API on the classpath.
- Server tests (`src/serverTest`) run inside the server with JUnit 5, on the main thread by
  default; `@OffMainThread` for tests that wait for the server.
- `ServerTest.dispatchCommand`, `sync`, `awaitTicks`, `recordEvents`.
- Results in the build output, JUnit XML reports and the server log per server.

### Fake players

- `Bots.join(name)`: bot clients (MCProtocolLib) that join over the real network protocol,
  through ViaProxy so they work on every version.
- Chat and commands, received messages (incl. translated vanilla messages), inventory GUIs
  (`awaitInventory`, `click`, `closeInventory`), quitting; cleaned up after each test.

### Other plugins

- `serverTestPlugin` configuration (Maven or files) and `spigotTesting.pluginUrls` (downloads)
  install other plugins next to yours; tests can use their APIs.

### Diagnostics

- The Minecraft EULA is checked before any server is built.
- A plugin that fails to load stops the run right away, with the errors from the server log.
