package io.github.larslewerenz.greeter;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** PlaceholderAPI placeholders: {@code %greeter_greetings%} is the number of greetings so far. */
final class GreeterExpansion extends PlaceholderExpansion {

    private final GreeterPlugin plugin;

    GreeterExpansion(GreeterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "greeter";
    }

    @Override
    public String getAuthor() {
        return "lars-lewerenz";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return params.equals("greetings") ? String.valueOf(plugin.getGreetings()) : null;
    }
}
