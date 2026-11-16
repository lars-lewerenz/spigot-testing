package io.github.larslewerenz.spigottesting.gradle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The PATH BuildTools gets on Windows. BuildTools checks whether "sh" can be started: if so, it
 * runs "bash applyPatches.sh" directly, and Windows looks in System32 before the PATH, where
 * "bash" is the WSL launcher, which fails without an installed distribution. If "sh" can't be
 * started, BuildTools downloads its own PortableGit and runs bash through it, which works
 * everywhere. So directories with sh.exe or bash.exe are left out of its PATH.
 */
final class WindowsPath {

    private WindowsPath() {
    }

    static String withoutShells(String path) {
        if (path == null) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String entry : path.split(Pattern.quote(File.pathSeparator), -1)) {
            if (entry.isBlank() || hasShell(entry)) {
                continue;
            }
            kept.add(entry);
        }
        return String.join(File.pathSeparator, kept);
    }

    private static boolean hasShell(String directory) {
        try {
            Path dir = Path.of(directory);
            return Files.isRegularFile(dir.resolve("sh.exe")) || Files.isRegularFile(dir.resolve("bash.exe"));
        } catch (RuntimeException e) {
            return false; // not a valid path: leave it alone
        }
    }
}
