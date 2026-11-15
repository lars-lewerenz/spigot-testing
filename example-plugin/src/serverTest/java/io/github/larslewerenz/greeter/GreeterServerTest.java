package io.github.larslewerenz.greeter;

import static io.github.larslewerenz.spigottesting.ServerTest.awaitTicks;
import static io.github.larslewerenz.spigottesting.ServerTest.dispatchCommand;
import static io.github.larslewerenz.spigottesting.ServerTest.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.larslewerenz.spigottesting.CommandResult;
import io.github.larslewerenz.spigottesting.OffMainThread;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
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
        // Watch the spawn events after every plugin has handled them, instead of looking for the
        // entities in the world: newer versions load a chunk's entities asynchronously, so a mob
        // that did spawn may not be visible through World#getEntities right away.
        SpawnRecorder recorder = new SpawnRecorder();
        Bukkit.getPluginManager().registerEvents(recorder, JavaPlugin.getProvidingPlugin(GreeterServerTest.class));
        World world = Bukkit.getWorlds().get(0);
        Location location = world.getSpawnLocation();
        Entity creeper = world.spawnEntity(location, EntityType.CREEPER);
        Entity zombie = world.spawnEntity(location, EntityType.ZOMBIE);
        try {
            assertEquals(Arrays.asList("CREEPER cancelled", "ZOMBIE spawned"), recorder.spawns);
        } finally {
            HandlerList.unregisterAll(recorder);
            creeper.remove();
            zombie.remove();
        }
    }

    public static final class SpawnRecorder implements Listener {
        final List<String> spawns = new ArrayList<>();

        @EventHandler(priority = EventPriority.MONITOR)
        public void onSpawn(CreatureSpawnEvent event) {
            spawns.add(event.getEntityType() + (event.isCancelled() ? " cancelled" : " spawned"));
        }
    }

    @Test
    @OffMainThread
    void nightIsSkipped() {
        World world = sync(() -> Bukkit.getWorlds().get(0));
        sync(() -> world.setTime(18000)); // midnight
        assertTrue(sync(world::getTime) >= 18000);

        awaitTicks(40); // the plugin checks once per second

        long time = sync(world::getTime);
        assertTrue(time < 12000, "still night after 2 seconds: time=" + time);
    }
}
