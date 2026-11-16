package io.github.larslewerenz.papergreeter;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** A small Paper plugin: a Brigadier command and an Adventure welcome message. */
public final class PaperGreeter extends JavaPlugin implements Listener {

    private int joins;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("papergreet", "Greets someone, Paper style", (BasicCommand) (source, args) -> {
                    String name = args.length > 0 ? args[0] : "stranger";
                    source.getSender().sendMessage(greeting(name));
                }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        joins++;
        event.getPlayer().sendMessage(Component.text("Welcome to Paper, " + event.getPlayer().getName() + "!", NamedTextColor.GOLD));
    }

    public static Component greeting(String name) {
        return Component.text("Hello from Paper, " + name + "!", NamedTextColor.AQUA);
    }

    public int joins() {
        return joins;
    }
}
