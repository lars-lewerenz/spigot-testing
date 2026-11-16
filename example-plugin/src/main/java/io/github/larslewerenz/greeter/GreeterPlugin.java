package io.github.larslewerenz.greeter;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class GreeterPlugin extends JavaPlugin implements Listener {

    private int greetings;
    private Sidebar sidebar;
    private Visitors visitors;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        sidebar = new Sidebar(getServer().getScoreboardManager().getNewScoreboard());
        visitors = new Visitors(new java.io.File(getDataFolder(), "visitors.yml"));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new GreeterMenu(this), this);
        getServer().getPluginManager().registerEvents(new SpawnArea(this), this);
        getServer().getScheduler().runTaskTimer(this, this::lockDay, 20L, 20L);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            // Only touch the expansion class when PlaceholderAPI is installed (soft dependency).
            new GreeterExpansion(this).register();
        }
    }

    @Override
    public void onDisable() {
        try {
            visitors.save();
        } catch (java.io.IOException e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not save visitors.yml", e);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("rules")) {
            Rules.send(sender, new java.io.File(getDataFolder(), "rules"));
            return true;
        }
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
        updateSidebar();
    }

    private void updateSidebar() {
        sidebar.update(getServer().getOnlinePlayers().size(), greetings);
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        String name = event.getPlayer().getName();
        for (String banned : getConfig().getStringList("banned-names")) {
            if (banned.equalsIgnoreCase(name)) {
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Greetings.format(getConfig().getString("banned-name-message"), name));
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage().toLowerCase(java.util.Locale.ROOT);
        for (String word : getConfig().getStringList("kick-words")) {
            if (message.contains(word.toLowerCase(java.util.Locale.ROOT))) {
                event.setCancelled(true);
                Player player = event.getPlayer();
                String reason = Greetings.format(getConfig().getString("kick-message"), player.getName());
                // Chat arrives off the main thread; kicking has to happen on it.
                getServer().getScheduler().runTask(this, () -> player.kickPlayer(reason));
                return;
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(Greetings.format(getConfig().getString("welcome"), player.getName()));
        String visit = visitors.visit(player.getName()) ? "first-visit" : "return-visit";
        player.sendMessage(Greetings.format(getConfig().getString(visit), player.getName()));
        player.sendTitle(Greetings.format(getConfig().getString("welcome-title"), player.getName()),
                Greetings.format(getConfig().getString("welcome-subtitle"), player.getName()));
        player.setPlayerListName(ChatColor.YELLOW + player.getName());
        sidebar.show(player);
        updateSidebar();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // The player still counts as online during the event.
        getServer().getScheduler().runTask(this, this::updateSidebar);
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
