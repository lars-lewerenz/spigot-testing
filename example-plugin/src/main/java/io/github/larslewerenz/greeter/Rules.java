package io.github.larslewerenz.greeter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/** /rules: every text file in plugins/Greeter/rules/, in file name order. */
final class Rules {

    private Rules() {
    }

    static void send(CommandSender sender, File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            sender.sendMessage("No rules configured.");
            return;
        }
        Arrays.sort(files);
        for (File file : files) {
            try {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    if (!line.trim().isEmpty()) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
                    }
                }
            } catch (IOException e) {
                sender.sendMessage("Could not read " + file.getName());
            }
        }
    }
}
