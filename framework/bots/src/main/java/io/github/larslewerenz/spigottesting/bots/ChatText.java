package io.github.larslewerenz.spigottesting.bots;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Turns chat components into the text a player would see, with {@code §} color codes. Servers
 * send some messages as translation keys that only the client resolves (Paper sends chat and
 * join messages that way), so the common vanilla ones are rendered here like a client would.
 */
final class ChatText {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Pattern REDUNDANT_CODE = Pattern.compile("§[0-9a-fk-or](?=§[0-9a-fr])", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_CODES = Pattern.compile("(§[0-9a-fk-or])+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?s");

    /** English texts of the vanilla translation keys that reach players in chat. */
    private static final Map<String, String> VANILLA = Map.ofEntries(
            Map.entry("chat.type.text", "<%s> %s"),
            Map.entry("chat.type.announcement", "[%s] %s"),
            Map.entry("chat.type.emote", "* %s %s"),
            Map.entry("chat.type.admin", "[%s: %s]"),
            Map.entry("chat.type.team.text", "%s <%s> %s"),
            Map.entry("chat.type.team.sent", "-> %s <%s> %s"),
            Map.entry("commands.message.display.incoming", "%s whispers to you: %s"),
            Map.entry("commands.message.display.outgoing", "You whisper to %s: %s"),
            Map.entry("multiplayer.player.joined", "%s joined the game"),
            Map.entry("multiplayer.player.joined.renamed", "%s (formerly known as %s) joined the game"),
            Map.entry("multiplayer.player.left", "%s left the game"),
            Map.entry("multiplayer.disconnect.kicked", "Kicked by an operator"));

    private ChatText() {
    }

    static String legacy(Component component) {
        return LEGACY.serialize(render(component));
    }

    /**
     * Drops color codes that have no effect: ones directly followed by a color code or reset
     * (which override them) and ones at the end. Scoreboards are full of them, since plugins use
     * color codes to make otherwise equal lines unique.
     */
    static String tidy(String legacy) {
        return TRAILING_CODES.matcher(REDUNDANT_CODE.matcher(legacy).replaceAll("")).replaceAll("");
    }

    static Component render(Component component) {
        List<Component> children = new ArrayList<>();
        for (Component child : component.children()) {
            children.add(render(child));
        }
        if (!(component instanceof TranslatableComponent translatable)) {
            return component.children(children);
        }
        List<Component> arguments = new ArrayList<>();
        for (TranslationArgument argument : translatable.arguments()) {
            arguments.add(render(argument.asComponent()));
        }
        String format = VANILLA.getOrDefault(translatable.key(), translatable.fallback());
        TextComponent.Builder text = Component.text().style(translatable.style());
        if (format == null) {
            // Unknown key: show the key and its arguments rather than nothing.
            text.append(Component.text(translatable.key()));
            for (Component argument : arguments) {
                text.append(Component.text(" ")).append(argument);
            }
        } else {
            Matcher m = ARGUMENT.matcher(format);
            int position = 0;
            int next = 0;
            while (m.find()) {
                text.append(Component.text(format.substring(position, m.start())));
                int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                if (index < arguments.size()) {
                    text.append(arguments.get(index));
                }
                position = m.end();
            }
            text.append(Component.text(format.substring(position)));
        }
        return text.append(children).build();
    }
}
