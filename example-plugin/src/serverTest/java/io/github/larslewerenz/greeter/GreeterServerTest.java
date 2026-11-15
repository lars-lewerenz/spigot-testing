package io.github.larslewerenz.greeter;

import static io.github.larslewerenz.spigottesting.ServerTest.awaitTicks;
import static io.github.larslewerenz.spigottesting.ServerTest.dispatchCommand;
import static io.github.larslewerenz.spigottesting.ServerTest.recordEvents;
import static io.github.larslewerenz.spigottesting.ServerTest.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.larslewerenz.spigottesting.CommandResult;
import io.github.larslewerenz.spigottesting.EventRecorder;
import io.github.larslewerenz.spigottesting.OffMainThread;
import java.util.Collections;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

/**
 * Server tests: run inside a real Spigot server, once per configured version. Test methods run on
 * the server main thread unless annotated with {@link OffMainThread}.
 *
 * <p>The plugin and the tests are loaded by different class loaders, so only the plugin's public
 * API is accessible here, even from the same package.
 */
class GreeterServerTest {

    private final GreeterPlugin plugin = JavaPlugin.getPlugin(GreeterPlugin.class);

    @Test
    void pluginIsEnabled() {
        assertTrue(Bukkit.getPluginManager().isPluginEnabled("Greeter"));
    }

    @Test
    void greetCommandGreetsByName() {
        int before = plugin.getGreetings();

        CommandResult result = dispatchCommand("greet Steve");

        assertTrue(result.success());
        assertEquals(Collections.singletonList(ChatColor.GREEN + "Hello, Steve!"), result.messages());
        assertEquals(before + 1, plugin.getGreetings());
    }

    @Test
    void greetCommandWithoutNameShowsUsage() {
        CommandResult result = dispatchCommand("greet");

        assertTrue(result.messages().contains("/greet <name>"), result.toString());
    }

    @Test
    void creeperSpawnsAreCancelled() {
        // Check the spawn events (as every plugin left them) instead of looking for the entities
        // in the world: newer versions load a chunk's entities asynchronously, so a mob that did
        // spawn may not be visible through World#getEntities right away.
        EventRecorder<CreatureSpawnEvent> spawns = recordEvents(CreatureSpawnEvent.class);
        World world = Bukkit.getWorlds().get(0);
        Location location = world.getSpawnLocation();
        Entity creeper = world.spawnEntity(location, EntityType.CREEPER);
        Entity zombie = world.spawnEntity(location, EntityType.ZOMBIE);
        try {
            assertEquals(2, spawns.events().size());
            assertTrue(spawns.events().get(0).isCancelled(), "creeper spawn must be cancelled");
            assertFalse(spawns.events().get(1).isCancelled(), "other mobs must still spawn");
        } finally {
            creeper.remove();
            zombie.remove();
        }
    }

    @Test
    @OffMainThread
    void nightIsSkipped() {
        World world = sync(() -> Bukkit.getWorlds().get(0));
        // Set and check in one go: between two separate calls the plugin may already have reset it.
        long midnight = sync(() -> {
            world.setTime(18000);
            return world.getTime();
        });
        assertEquals(18000, midnight);

        awaitTicks(40); // the plugin checks once per second

        long time = sync(world::getTime);
        assertTrue(time < 12000, "still night after 2 seconds: time=" + time);
    }
}
