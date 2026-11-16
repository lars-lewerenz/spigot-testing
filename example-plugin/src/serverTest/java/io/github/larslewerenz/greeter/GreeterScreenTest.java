package io.github.larslewerenz.greeter;

import static io.github.larslewerenz.spigottesting.ServerTest.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.larslewerenz.spigottesting.Bot;
import io.github.larslewerenz.spigottesting.Bots;
import io.github.larslewerenz.spigottesting.ClientView;
import io.github.larslewerenz.spigottesting.OffMainThread;
import java.util.Arrays;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

/** What players see besides chat: a title when they join, the sidebar and the tab list. */
@OffMainThread
class GreeterScreenTest {

    private final GreeterPlugin plugin = JavaPlugin.getPlugin(GreeterPlugin.class);

    @Test
    void joiningPlayersSeeAWelcomeTitle() {
        Bot alex = Bots.join("Alex");

        ClientView view = alex.awaitView(v -> v.subtitle() != null);

        assertEquals(ChatColor.GOLD + "Welcome!", view.title());
        assertEquals(ChatColor.YELLOW + "Have fun, Alex", view.subtitle());
    }

    @Test
    void theSidebarCountsPlayersAndGreetings() {
        Bot alex = Bots.join("Alex");
        Bot bob = Bots.join("Bob");
        int greetings = sync(plugin::getGreetings) + 1;

        alex.chat("/greet Steve");

        ClientView view = bob.awaitView(v -> v.sidebar().contains("Greetings: " + greetings));
        assertEquals(ChatColor.GOLD + "Greeter", view.sidebarTitle());
        assertEquals(Arrays.asList("Online: 2", "Greetings: " + greetings), view.sidebar());
    }

    @Test
    void playersAreYellowInTheTabList() {
        Bot alex = Bots.join("Alex");
        Bots.join("Bob");

        alex.awaitView(v -> v.tabList().containsAll(Arrays.asList(ChatColor.YELLOW + "Alex", ChatColor.YELLOW + "Bob")));
    }
}
