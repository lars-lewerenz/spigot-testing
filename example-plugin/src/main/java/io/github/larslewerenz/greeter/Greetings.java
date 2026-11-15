package io.github.larslewerenz.greeter;

import org.bukkit.ChatColor;

final class Greetings {

    private Greetings() {
    }

    /** Replaces {@code %name%} and translates {@code &} color codes. */
    static String format(String template, String name) {
        return ChatColor.translateAlternateColorCodes('&', template.replace("%name%", name));
    }
}
