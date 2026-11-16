package io.github.larslewerenz.greeter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.larslewerenz.spigottesting.AfterRestart;
import io.github.larslewerenz.spigottesting.Bot;
import io.github.larslewerenz.spigottesting.Bots;
import io.github.larslewerenz.spigottesting.OffMainThread;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

/**
 * Greeter remembers who has joined before, across restarts: it saves visitors.yml when the
 * server stops and reads it when it starts. "Newcomer" is used by these two tests only.
 */
@OffMainThread
class GreeterRestartTest {

    @Test
    void newcomersAreMetForTheFirstTime() {
        Bot newcomer = Bots.join("Newcomer");

        assertEquals(ChatColor.AQUA + "Nice to meet you, Newcomer!", newcomer.awaitMessage("Nice to meet you"));
    }

    @Test
    @AfterRestart // runs once all other tests are done and the server has been restarted
    void theyAreRememberedAfterARestart() {
        Bot newcomer = Bots.join("Newcomer");

        assertEquals(ChatColor.AQUA + "Good to see you again, Newcomer!", newcomer.awaitMessage("again"));
    }
}
