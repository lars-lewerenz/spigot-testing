package io.github.larslewerenz.papergreeter;

import static io.github.larslewerenz.spigottesting.ServerTest.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.viaversion.viaversion.api.Via;
import io.github.larslewerenz.spigottesting.Bot;
import io.github.larslewerenz.spigottesting.Bots;
import io.github.larslewerenz.spigottesting.ClientView;
import io.github.larslewerenz.spigottesting.OffMainThread;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

/**
 * Players on other client versions, let in by ViaVersion, ViaBackwards and ViaRewind (installed
 * on these servers, see build.gradle.kts): what PaperGreeter sends must reach them too.
 */
@OffMainThread
class OtherClientsTest {

    @Test
    void theServerSeesTheClientVersion() {
        Bot alex = Bots.join("Alex", "1.12.2");

        assertEquals("1.12.2", sync(() -> Via.getAPI().getPlayerProtocolVersion(alex.player().getUniqueId()).getName()));
    }

    @Test
    void oneEightPlayersAreWelcomed() {
        Bot oldie = Bots.join("Oldie", "1.8.8");

        assertEquals(ChatColor.GOLD + "Welcome to Paper, Oldie!", oldie.awaitMessage("Welcome"));
        ClientView view = oldie.awaitView(v -> v.actionBar() != null);
        assertTrue(view.actionBar().startsWith(ChatColor.GREEN + "You are player #"), view.actionBar());
    }

    @Test
    void oneTwentyPlayersSeeTheBossBar() {
        Bot alex = Bots.join("Alex", "1.20.4");

        ClientView view = alex.awaitView(v -> !v.bossBars().isEmpty());

        assertEquals(ChatColor.LIGHT_PURPLE + "Paper week", view.bossBars().get(0).title());
    }
}
