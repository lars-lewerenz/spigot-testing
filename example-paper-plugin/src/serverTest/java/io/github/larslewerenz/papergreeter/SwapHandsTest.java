package io.github.larslewerenz.papergreeter;

import static io.github.larslewerenz.spigottesting.ServerTest.awaitTicks;
import static io.github.larslewerenz.spigottesting.ServerTest.recordEvents;
import static io.github.larslewerenz.spigottesting.ServerTest.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.larslewerenz.spigottesting.Bot;
import io.github.larslewerenz.spigottesting.Bots;
import io.github.larslewerenz.spigottesting.EventRecorder;
import io.github.larslewerenz.spigottesting.OffMainThread;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** Swapping hands (F) needs an off hand, so 1.9+; this plugin's API has the event to check it. */
@OffMainThread
class SwapHandsTest {

    @Test
    void swappingHandsMovesTheHeldItemToTheOffHand() {
        Bot alex = Bots.join("Alex");
        sync(() -> {
            alex.player().getInventory().setItem(0, new ItemStack(Material.BREAD));
            alex.player().getInventory().setItemInOffHand(null);
        });
        alex.selectHotbarSlot(0);
        EventRecorder<PlayerSwapHandItemsEvent> swaps = recordEvents(PlayerSwapHandItemsEvent.class);

        alex.swapHands();

        assertEquals(Material.BREAD, swaps.awaitEvent().getOffHandItem().getType());
        awaitTicks(2);
        assertEquals(Material.BREAD, sync(() -> alex.player().getInventory().getItemInOffHand().getType()));
    }
}
