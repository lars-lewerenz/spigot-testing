package io.github.larslewerenz.spigottesting.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** BuildTools' PATH on Windows (layout simulated): no directories with sh.exe or bash.exe. */
class WindowsPathTest {

    @TempDir
    Path dir;

    @Test
    void dropsDirectoriesWithShells() throws IOException {
        Path gitCmd = Files.createDirectories(dir.resolve("Git/cmd"));
        Files.createFile(gitCmd.resolve("git.exe"));
        Path gitBin = Files.createDirectories(dir.resolve("Git/bin"));
        Files.createFile(gitBin.resolve("bash.exe"));
        Path usrBin = Files.createDirectories(dir.resolve("Git/usr/bin"));
        Files.createFile(usrBin.resolve("sh.exe"));
        Path java = Files.createDirectories(dir.resolve("java/bin"));
        String path = String.join(File.pathSeparator, java.toString(), gitBin.toString(), usrBin.toString(), "", gitCmd.toString());

        assertEquals(java + File.pathSeparator + gitCmd, WindowsPath.withoutShells(path));
    }

    @Test
    void handlesMissingPath() {
        assertEquals("", WindowsPath.withoutShells(null));
    }
}
