package io.github.larslewerenz.greeter;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * The area around the world spawn: nobody without the greeter.build permission may build there,
 * and players walking out of it are told so.
 */
final class SpawnArea implements Listener {

    private final GreeterPlugin plugin;

    SpawnArea(GreeterPlugin plugin) {
        this.plugin = plugin;
    }

    boolean contains(Location location) {
        Location spawn = location.getWorld().getSpawnLocation();
        double dx = location.getX() - spawn.getX();
        double dz = location.getZ() - spawn.getZ();
        double radius = plugin.getConfig().getDouble("spawn-radius");
        return dx * dx + dz * dz <= radius * radius;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        protect(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        protect(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    private void protect(Player player, Location block, Cancellable event) {
        if (contains(block) && !player.hasPermission("greeter.build")) {
            event.setCancelled(true);
            player.sendMessage(Greetings.format(plugin.getConfig().getString("spawn-protected-message"), player.getName()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (contains(event.getFrom()) && !contains(event.getTo())) {
            event.getPlayer().sendMessage(Greetings.format(plugin.getConfig().getString("left-spawn-message"), event.getPlayer().getName()));
        }
    }
}
