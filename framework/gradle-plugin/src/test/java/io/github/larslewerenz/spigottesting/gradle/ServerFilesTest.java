package io.github.larslewerenz.spigottesting.gradle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Laying src/serverTest/server* onto the server directory. */
class ServerFilesTest {

    @TempDir
    Path dir;

    private Path file(String path, String content) throws IOException {
        Path file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    @Test
    void copiesWholeTreesAndLaterLayersWin() throws IOException {
        file("server/plugins/MyPlugin/config.yml", "a: 1\n");
        file("server/plugins/MyPlugin/messages/en.yml", "hello: Hello\n");
        file("server/plugins/MyPlugin/messages/de.yml", "hello: Hallo\n");
        file("server-paper/plugins/MyPlugin/config.yml", "a: 2\n");
        Files.write(file("server/plugins/MyPlugin/data.db", "").getParent().resolve("data.db"), new byte[] {0, 1, 2, (byte) 255});
        Path server = Files.createDirectories(dir.resolve("run"));

        List<String> warnings = ServerFiles.apply(
                List.of(dir.resolve("server"), dir.resolve("server-paper"), dir.resolve("server-26.3")),
                server, ServerFiles.defaultProperties(25565));

        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals("a: 2\n", Files.readString(server.resolve("plugins/MyPlugin/config.yml")));
        assertEquals("hello: Hallo\n", Files.readString(server.resolve("plugins/MyPlugin/messages/de.yml")));
        assertEquals("hello: Hello\n", Files.readString(server.resolve("plugins/MyPlugin/messages/en.yml")));
        assertArrayEquals(new byte[] {0, 1, 2, (byte) 255}, Files.readAllBytes(server.resolve("plugins/MyPlugin/data.db")));
    }

    @Test
    void mergesServerPropertiesAndKeepsProtectedKeys() throws IOException {
        file("server/server.properties", "# comment\ndifficulty=3\nmotd=My test server\n");
        file("server-paper/server.properties", "difficulty=2\nserver-port=1234\nonline-mode=true\n");
        Map<String, String> properties = ServerFiles.defaultProperties(25565);

        List<String> warnings = ServerFiles.apply(List.of(dir.resolve("server"), dir.resolve("server-paper")),
                Files.createDirectories(dir.resolve("run")), properties);

        assertEquals("2", properties.get("difficulty"));
        assertEquals("My test server", properties.get("motd"));
        assertEquals("25565", properties.get("server-port"));
        assertEquals("false", properties.get("online-mode"));
        assertEquals("flat", properties.get("level-type"));
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("online-mode"), warnings.toString());
        assertFalse(Files.exists(dir.resolve("run/server.properties")), "written by the caller after merging");
    }

    @Test
    void missingLayersAreSkipped() throws IOException {
        Path server = Files.createDirectories(dir.resolve("run"));

        assertTrue(ServerFiles.apply(List.of(dir.resolve("does-not-exist")), server, ServerFiles.defaultProperties(1)).isEmpty());
    }

    @Test
    void writesPropertiesInOrder() {
        String text = ServerFiles.toPropertiesFile(ServerFiles.defaultProperties(4242));

        assertTrue(text.startsWith("online-mode=false\nserver-ip=127.0.0.1\nserver-port=4242\n"), text);
    }
}
