package io.github.larslewerenz.spigottesting.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

final class Logs {

    private Logs() {
    }

    static String tail(File file, int lines) {
        try {
            List<String> all = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            return String.join("\n", all.subList(Math.max(0, all.size() - lines), all.size()));
        } catch (IOException e) {
            return "(could not read " + file + ": " + e + ")";
        }
    }
}
