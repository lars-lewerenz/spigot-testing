package io.github.larslewerenz.greeter;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class GreeterPlugin extends JavaPlugin implements Listener {

    private int greetings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::lockDay, 20L, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return false; // Bukkit then sends the usage from plugin.yml
        }
        sender.sendMessage(Greetings.format(getConfig().getString("greeting"), args[0]));
        greetings++;
        return true;
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
