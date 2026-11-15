package io.github.larslewerenz.greeter;

import static io.github.larslewerenz.spigottesting.ServerTest.dispatchCommand;
import static io.github.larslewerenz.spigottesting.ServerTest.recordEvents;
import static io.github.larslewerenz.spigottesting.ServerTest.setConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.larslewerenz.spigottesting.EventRecorder;
import io.github.larslewerenz.spigottesting.ServerTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

/**
 * Server files from src/serverTest/server* and config changes within a test.
 *
 * <ul>
 *   <li>src/serverTest/server/plugins/Greeter/rules/: copied into the plugin's folder on every server
 *   <li>src/serverTest/server-paper/plugins/Greeter/rules/: one more rule, on Paper only
 *   <li>src/serverTest/server/server.properties: difficulty=3, merged into the server's settings
 * </ul>
 */
class GreeterConfigTest {

    private final GreeterPlugin plugin = JavaPlugin.getPlugin(GreeterPlugin.class);

    @Test
    void rulesComeFromTheServerFiles() {
        List<String> expected = new ArrayList<>(Arrays.asList("Be nice to each other.", "No griefing."));
        if (ServerTest.serverType().equals("paper")) {
            expected.add("Paper servers have one more rule: have fun.");
        }

        assertEquals(expected, dispatchCommand("rules").messages());
    }

    @Test
    void serverPropertiesAreMerged() {
        assertEquals(Difficulty.HARD, Bukkit.getWorlds().get(0).getDifficulty());
        // the framework's own settings are still there
        assertEquals("flat", Bukkit.getWorldType().toLowerCase().replace("minecraft:", ""));
    }

    @Test
    void creepersSpawnWhenBlockingIsSwitchedOff() {
        setConfig(plugin, "block-creepers", false); // restored after the test

        EventRecorder<CreatureSpawnEvent> spawns = recordEvents(CreatureSpawnEvent.class);
        World world = Bukkit.getWorlds().get(0);
        Entity creeper = world.spawnEntity(world.getSpawnLocation(), EntityType.CREEPER);
        try {
            assertFalse(spawns.events().get(0).isCancelled());
        } finally {
            creeper.remove();
        }
    }

    @Test
    void configChangesAreUndoneAfterEachTest() {
        // runs in any order relative to the test above: the value is back to the default either way
        assertTrue(plugin.getConfig().getBoolean("block-creepers"));
    }
}
