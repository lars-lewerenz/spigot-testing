package io.github.larslewerenz.greeter;

import static io.github.larslewerenz.spigottesting.ServerTest.awaitTicks;
import static io.github.larslewerenz.spigottesting.ServerTest.dispatchCommand;
import static io.github.larslewerenz.spigottesting.ServerTest.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.larslewerenz.spigottesting.CommandResult;
import io.github.larslewerenz.spigottesting.OffMainThread;
import java.util.Collections;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
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
        World world = Bukkit.getWorlds().get(0);
        Location location = world.getSpawnLocation();
        location.getChunk().load();

        world.spawnEntity(location, EntityType.CREEPER);
        Zombie zombie = (Zombie) world.spawnEntity(location, EntityType.ZOMBIE);
        try {
            assertTrue(world.getEntitiesByClass(Creeper.class).isEmpty(), "a creeper was spawned");
            assertEquals(1, world.getEntitiesByClass(Zombie.class).size(), "other mobs must still spawn");
        } finally {
            zombie.remove();
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
