package io.github.larslewerenz.greeter;

import java.util.Collections;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * A chest GUI (/menu, or right-click the menu compass): clicking the paper greets the player.
 * Items can't be taken out. Recognized by its holder rather than its title, which works the same
 * on every server version.
 */
public final class GreeterMenu implements Listener {

    public static final String TITLE = ChatColor.DARK_GREEN + "Greeter Menu";
    public static final int GREET_SLOT = 13;
    public static final String ITEM_NAME = ChatColor.GREEN + "Greeter Menu";
    /** Where joining players get the menu compass, if that slot is free. */
    private static final int ITEM_SLOT = 8;

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

    static ItemStack item() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName(ITEM_NAME);
        compass.setItemMeta(meta);
        return compass;
    }

    public static boolean isItem(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && item.hasItemMeta()
                && ITEM_NAME.equals(item.getItemMeta().getDisplayName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("menu-item")) {
            return;
        }
        PlayerInventory inventory = event.getPlayer().getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (isItem(item)) {
                return;
            }
        }
        if (inventory.getItem(ITEM_SLOT) == null) {
            inventory.setItem(ITEM_SLOT, item());
        } else {
            inventory.addItem(item());
        }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && isItem(event.getItem())) {
            event.setCancelled(true);
            open(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
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
