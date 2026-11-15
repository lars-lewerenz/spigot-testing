package io.github.larslewerenz.spigottesting.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

final class Logs {

    private Logs() {
    }

    private static final java.util.regex.Pattern PROBLEM = java.util.regex.Pattern.compile(
            "ERROR\\]|SEVERE|WARN\\].*(Could not|Error|Exception)|Could not load|Error occurred while|Exception|Caused by");

    /**
     * What a reader needs when a server ended without results: the lines that report problems
     * (plugins that failed to load or enable, exceptions), or the end of the log if there are none.
     */
    static String problems(File file, int lines) {
        try {
            List<String> all = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> problems = all.stream().filter(line -> PROBLEM.matcher(line).find()).toList();
            if (problems.isEmpty()) {
                return "Last lines of the server log:\n" + tail(file, lines);
            }
            return "Problems in the server log:\n"
                    + String.join("\n", problems.subList(0, Math.min(lines, problems.size())))
                    + (problems.size() > lines ? "\n… (" + (problems.size() - lines) + " more)" : "");
        } catch (IOException e) {
            return "(could not read " + file + ": " + e + ")";
        }
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
