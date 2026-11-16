package io.github.larslewerenz.papergreeter;

import static io.github.larslewerenz.spigottesting.ServerTest.dispatchCommand;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.larslewerenz.spigottesting.Bot;
import io.github.larslewerenz.spigottesting.Bots;
import io.github.larslewerenz.spigottesting.ClientView;
import io.github.larslewerenz.spigottesting.OffMainThread;
import io.github.larslewerenz.spigottesting.ServerTest;
import io.github.larslewerenz.spigottesting.ServerType;
import java.util.Collections;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

/** Server tests for a Paper plugin (paper-plugin.yml): its classes are reachable from tests. */
class PaperGreeterTest {

    @Test
    void pluginIsEnabled() {
        assertTrue(Bukkit.getPluginManager().isPluginEnabled("PaperGreeter"));
        assertEquals(ServerType.PAPER, ServerTest.serverType());
    }

    @Test
    void brigadierCommandAnswers() {
        assertEquals(Collections.singletonList(ChatColor.AQUA + "Hello from Paper, Alex!"),
                dispatchCommand("papergreet Alex").messages());
    }

    @Test
    @OffMainThread
    void joiningPlayersAreWelcomed() {
        PaperGreeter plugin = JavaPlugin.getPlugin(PaperGreeter.class);
        int before = ServerTest.sync(plugin::joins);

        Bot alex = Bots.join("Alex");

        assertEquals(ChatColor.GOLD + "Welcome to Paper, Alex!", alex.awaitMessage("Welcome"));
        assertEquals(before + 1, (int) ServerTest.sync(plugin::joins));
    }

    @Test
    @OffMainThread
    void joiningPlayersSeeTheActionBarBossBarAndTabList() {
        Bot alex = Bots.join("Alex");

        ClientView view = alex.awaitView(v -> v.actionBar() != null && !v.bossBars().isEmpty() && v.tabHeader() != null);

        assertTrue(view.actionBar().startsWith(ChatColor.GREEN + "You are player #"));
        assertEquals(ChatColor.LIGHT_PURPLE + "Paper week", view.bossBars().get(0).title());
        assertEquals(0.5f, view.bossBars().get(0).progress());
        assertEquals(ChatColor.AQUA + "PaperGreeter", view.tabHeader());
        assertEquals("Have fun!", view.tabFooter());
    }
}
