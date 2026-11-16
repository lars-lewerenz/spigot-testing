package io.github.larslewerenz.spigottesting.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Finding Git Bash from git.exe on the PATH (Windows layouts, simulated). */
class GitBashTest {

    @TempDir
    Path dir;

    private Path gitInstall() throws IOException {
        Path git = dir.resolve("Git");
        Files.createDirectories(git.resolve("cmd"));
        Files.createDirectories(git.resolve("bin"));
        Files.createDirectories(git.resolve("mingw64/bin"));
        Files.createFile(git.resolve("cmd/git.exe"));
        Files.createFile(git.resolve("mingw64/bin/git.exe"));
        Files.createFile(git.resolve("bin/bash.exe"));
        return git;
    }

    @Test
    void findsBashFromGitInCmd() throws IOException {
        Path git = gitInstall();
        String path = dir.resolve("System32") + File.pathSeparator + git.resolve("cmd");

        assertEquals(git.resolve("bin").toString(), GitBash.directory(path, null));
    }

    @Test
    void findsBashFromGitInMingw() throws IOException {
        Path git = gitInstall();

        assertEquals(git.resolve("bin").toString(), GitBash.directory(git.resolve("mingw64/bin").toString(), null));
    }

    @Test
    void fallsBackToProgramFiles() throws IOException {
        gitInstall();

        assertEquals(dir.resolve("Git/bin").toString(), GitBash.directory("", dir.toString()));
    }

    @Test
    void nullWithoutGit() {
        assertNull(GitBash.directory(dir.toString(), dir.resolve("nothing").toString()));
    }
}
