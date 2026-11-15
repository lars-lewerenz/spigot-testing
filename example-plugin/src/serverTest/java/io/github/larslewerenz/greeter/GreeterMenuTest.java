package io.github.larslewerenz.greeter;

import static io.github.larslewerenz.spigottesting.ServerTest.awaitTicks;
import static io.github.larslewerenz.spigottesting.ServerTest.recordEvents;
import static io.github.larslewerenz.spigottesting.ServerTest.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.larslewerenz.spigottesting.Bot;
import io.github.larslewerenz.spigottesting.Bots;
import io.github.larslewerenz.spigottesting.EventRecorder;
import io.github.larslewerenz.spigottesting.OffMainThread;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.jupiter.api.Test;

/** The /menu chest GUI, clicked by a bot like a real player. */
@OffMainThread
class GreeterMenuTest {

    @Test
    void clickingThePaperGreetsAndClosesTheMenu() {
        Bot alex = Bots.join("Alex");
        alex.chat("/menu");
        assertEquals(GreeterMenu.TITLE, alex.awaitInventory("Greeter Menu"));

        alex.click(GreeterMenu.GREET_SLOT);

        assertEquals(ChatColor.GREEN + "Hello, Alex!", alex.awaitMessage("Hello"));
        awaitClosed(alex);
    }

    @Test
    void itemsCannotBeTakenOutOfTheMenu() {
        Bot alex = Bots.join("Alex");
        EventRecorder<InventoryClickEvent> clicks = recordEvents(InventoryClickEvent.class);
        alex.chat("/menu");
        alex.awaitInventory("Greeter Menu");

        alex.click(GreeterMenu.GREET_SLOT, ClickType.SHIFT_LEFT);

        InventoryClickEvent click = clicks.awaitEvent();
        assertEquals(GreeterMenu.GREET_SLOT, click.getRawSlot());
        assertEquals(ClickType.SHIFT_LEFT, click.getClick());
        assertTrue(click.isCancelled(), "the menu must cancel clicks");
        assertNull(sync(() -> firstPaper(alex)), "the paper ended up in the player's inventory");
    }

    private static Integer firstPaper(Bot bot) {
        int slot = bot.player().getInventory().first(Material.PAPER);
        return slot < 0 ? null : slot;
    }

    private static void awaitClosed(Bot bot) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (bot.openInventoryTitle() != null) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("the menu is still open on the client");
            }
            awaitTicks(1);
        }
    }
}
