package io.github.larslewerenz.spigottesting.internal;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.bukkit.Server;
import org.bukkit.World;

/**
 * Since 1.21.11, servers ignore {@code spawn-monsters} in server.properties: it became the game
 * rule {@code spawn_monsters}. This applies the property to the rule, so the test servers' default
 * (no natural monster spawning, set by the Gradle plugin) or a project's own value holds on every
 * version. Older servers have no such rule and read the property themselves. Runs on the first
 * tick, before the tests start.
 */
final class MonsterSpawning {

    private static final String PROPERTY = "spawn-monsters";
    private static final String RULE = "spawn_monsters";

    private MonsterSpawning() {
    }

    static void applyProperty(Server server) {
        Properties properties = new Properties();
        try (InputStream in = new FileInputStream("server.properties")) {
            properties.load(in);
        } catch (IOException e) {
            return;
        }
        String value = properties.getProperty(PROPERTY);
        if (value == null) {
            return;
        }
        World world = server.getWorlds().get(0);
        if (world.isGameRule(RULE)) {
            // The command, not World#setGameRuleValue: Spigot removed that method in 1.21.11.
            // It sets the rule for the main world, the one tests use.
            server.dispatchCommand(server.getConsoleSender(), "gamerule " + RULE + " " + value.trim());
        }
    }
}
