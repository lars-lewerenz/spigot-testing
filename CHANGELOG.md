# Changelog

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
