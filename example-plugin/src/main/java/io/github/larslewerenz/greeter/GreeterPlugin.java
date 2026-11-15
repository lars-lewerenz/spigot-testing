package io.github.larslewerenz.greeter;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class GreeterPlugin extends JavaPlugin implements Listener {

    private int greetings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new GreeterMenu(this), this);
        getServer().getScheduler().runTaskTimer(this, this::lockDay, 20L, 20L);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            // Only touch the expansion class when PlaceholderAPI is installed (soft dependency).
            new GreeterExpansion(this).register();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("menu")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can open the menu.");
                return true;
            }
            GreeterMenu.open((Player) sender);
            return true;
        }
        if (args.length != 1) {
            return false; // Bukkit then sends the usage from plugin.yml
        }
        greet(sender, args[0]);
        return true;
    }

    void greet(CommandSender sender, String name) {
        sender.sendMessage(Greetings.format(getConfig().getString("greeting"), name));
        greetings++;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(Greetings.format(getConfig().getString("welcome"), player.getName()));
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() == EntityType.CREEPER && getConfig().getBoolean("block-creepers")) {
            event.setCancelled(true);
        }
    }

    private void lockDay() {
        if (!getConfig().getBoolean("lock-day")) {
            return;
        }
        for (World world : getServer().getWorlds()) {
            if (DayLock.isNight(world.getTime())) {
                world.setTime(DayLock.MORNING);
            }
        }
    }

    public int getGreetings() {
        return greetings;
    }
}
