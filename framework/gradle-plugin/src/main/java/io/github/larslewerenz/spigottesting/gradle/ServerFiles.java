package io.github.larslewerenz.spigottesting.gradle;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lays the project's server files onto a fresh server directory: every file of every layer is
 * copied (later layers win), except {@code server.properties}, whose keys are merged into the
 * framework's own settings.
 */
final class ServerFiles {

    static final String PROPERTIES = "server.properties";

    /** Keys the framework has to control: the bots connect to this address in offline mode. */
    static final Set<String> PROTECTED = Set.of("server-port", "server-ip", "online-mode");

    private ServerFiles() {
    }

    /**
     * Copies the layers (directories that don't exist are skipped) into {@code serverDir} and
     * merges their {@code server.properties} into {@code properties}. Returns warnings.
     */
    static List<String> apply(List<Path> layers, Path serverDir, Map<String, String> properties) throws IOException {
        List<String> warnings = new ArrayList<>();
        for (Path layer : layers) {
            if (!Files.isDirectory(layer)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(layer)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path file : files) {
                Path relative = layer.relativize(file);
                if (relative.toString().equals(PROPERTIES)) {
                    merge(file, properties, warnings);
                    continue;
                }
                Path target = serverDir.resolve(relative.toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return warnings;
    }

    private static void merge(Path file, Map<String, String> properties, List<String> warnings) throws IOException {
        Properties overrides = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            overrides.load(in);
        }
        for (String key : overrides.stringPropertyNames().stream().sorted().toList()) {
            if (PROTECTED.contains(key)) {
                warnings.add(file + ": ignoring " + key + ", which spigot-testing sets itself");
            } else {
                properties.put(key, overrides.getProperty(key));
            }
        }
    }

    static String toPropertiesFile(Map<String, String> properties) {
        StringBuilder text = new StringBuilder();
        properties.forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        return text.toString();
    }

    /** The framework's server.properties defaults. */
    static Map<String, String> defaultProperties(int port) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("online-mode", "false");
        properties.put("server-ip", "127.0.0.1");
        properties.put("server-port", String.valueOf(port));
        properties.put("level-type", "flat");
        properties.put("generate-structures", "false");
        properties.put("allow-nether", "false");
        properties.put("spawn-monsters", "false");
        properties.put("spawn-animals", "false");
        properties.put("spawn-npcs", "false");
        properties.put("spawn-protection", "0");
        properties.put("view-distance", "4");
        properties.put("simulation-distance", "4");
        properties.put("max-players", "100");
        properties.put("enforce-secure-profile", "false"); // bots send unsigned chat
        properties.put("prevent-proxy-connections", "false");
        properties.put("white-list", "false"); // 26.x enables the whitelist by default
        properties.put("enforce-whitelist", "false");
        properties.put("enable-query", "false");
        properties.put("enable-rcon", "false");
        properties.put("snooper-enabled", "false");
        properties.put("motd", "spigot-testing");
        return properties;
    }
}
