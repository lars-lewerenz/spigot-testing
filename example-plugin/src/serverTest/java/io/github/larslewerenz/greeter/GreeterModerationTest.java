package io.github.larslewerenz.greeter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.larslewerenz.spigottesting.Bot;
import io.github.larslewerenz.spigottesting.Bots;
import io.github.larslewerenz.spigottesting.OffMainThread;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

/** Players the server turns away at login, and players it kicks later. */
@OffMainThread
class GreeterModerationTest {

    @Test
    void bannedNamesAreTurnedAwayAtLogin() {
        String reason = Bots.expectRejection("Herobrine");

        assertEquals(ChatColor.RED + "The name Herobrine is not allowed here.", reason);
    }

    @Test
    void playersWhoTalkAboutGriefingAreKicked() {
        Bot alex = Bots.join("Alex");
        Bot bob = Bots.join("Bob");

        alex.chat("let's grief bob's house");

        assertEquals(ChatColor.RED + "No griefing, Alex!", alex.awaitKick());
        assertFalse(alex.isOnline());
        assertFalse(bob.messages().stream().anyMatch(m -> m.contains("grief")));
    }
}
