package io.github.larslewerenz.spigottesting.gradle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Finds Git for Windows' bash, which BuildTools needs to apply its patches. */
final class GitBash {

    private GitBash() {
    }

    /** The directory containing Git Bash's bash.exe, or null. */
    static String directory() {
        return directory(System.getenv("PATH"), System.getenv("ProgramFiles"));
    }

    static String directory(String path, String programFiles) {
        List<Path> roots = new ArrayList<>();
        if (path != null) {
            for (String entry : path.split(File.pathSeparator)) {
                if (entry.isBlank() || !Files.isRegularFile(Path.of(entry, "git.exe"))) {
                    continue;
                }
                // git.exe is in <Git>\cmd, <Git>\bin or <Git>\mingw64\bin
                Path dir = Path.of(entry).toAbsolutePath();
                roots.add(dir.getParent());
                if (dir.getParent() != null) {
                    roots.add(dir.getParent().getParent());
                }
            }
        }
        if (programFiles != null) {
            roots.add(Path.of(programFiles, "Git"));
        }
        for (Path root : roots) {
            if (root != null && Files.isRegularFile(root.resolve("bin").resolve("bash.exe"))) {
                return root.resolve("bin").toString();
            }
        }
        return null;
    }
}
