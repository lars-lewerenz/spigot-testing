package io.github.larslewerenz.greeter;

import static io.github.larslewerenz.spigottesting.ServerTest.dispatchCommand;
import static io.github.larslewerenz.spigottesting.ServerTest.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.larslewerenz.spigottesting.Bot;
import io.github.larslewerenz.spigottesting.Bots;
import io.github.larslewerenz.spigottesting.OffMainThread;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

/** Integration with other plugins installed on the test server (see serverTestPlugin / pluginUrls). */
class GreeterIntegrationTest {

    @Test
    void otherPluginsAreInstalled() {
        assertTrue(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"));
        assertTrue(Bukkit.getPluginManager().isPluginEnabled("Vault"));
    }

    @Test
    @OffMainThread
    void greetingsPlaceholderCountsGreetings() {
        Bot alex = Bots.join("Alex");
        GreeterPlugin plugin = JavaPlugin.getPlugin(GreeterPlugin.class);
        dispatchCommand("greet Steve");

        String count = sync(() -> PlaceholderAPI.setPlaceholders(alex.player(), "%greeter_greetings%"));

        assertEquals(String.valueOf(plugin.getGreetings()), count);
    }
}
