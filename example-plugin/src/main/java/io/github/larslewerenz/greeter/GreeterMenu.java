package io.github.larslewerenz.greeter;

import java.util.Collections;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * A chest GUI (/menu): clicking the paper greets the player. Items can't be taken out. Recognized
 * by its holder rather than its title, which works the same on every server version.
 */
public final class GreeterMenu implements Listener {

    public static final String TITLE = ChatColor.DARK_GREEN + "Greeter Menu";
    public static final int GREET_SLOT = 13;

    private final GreeterPlugin plugin;

    GreeterMenu(GreeterPlugin plugin) {
        this.plugin = plugin;
    }

    static void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 27, TITLE);
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Greet me");
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "Click to be greeted"));
        paper.setItemMeta(meta);
        inventory.setItem(GREET_SLOT, paper);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() == GREET_SLOT) {
            HumanEntity player = event.getWhoClicked();
            plugin.greet(player, player.getName());
            // Closing an inventory inside its click event is not allowed; do it on the next tick.
            Bukkit.getScheduler().runTask(plugin, player::closeInventory);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
