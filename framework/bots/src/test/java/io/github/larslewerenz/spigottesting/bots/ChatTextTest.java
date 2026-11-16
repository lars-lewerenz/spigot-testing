package io.github.larslewerenz.spigottesting.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class ChatTextTest {

    @Test
    void plainTextKeepsItsColours() {
        Component message = Component.text("Welcome, ", NamedTextColor.YELLOW)
                .append(Component.text("Alex", NamedTextColor.GOLD, TextDecoration.BOLD));

        assertEquals("§eWelcome, §6§lAlex", ChatText.legacy(message));
    }

    @Test
    void vanillaChatIsRenderedLikeTheClientWould() {
        Component chat = Component.translatable("chat.type.text", Component.text("Alex"), Component.text("hi bob"));

        assertEquals("<Alex> hi bob", ChatText.legacy(chat));
    }

    @Test
    void joinMessagesKeepTheirStyle() {
        Component joined = Component.translatable("multiplayer.player.joined", NamedTextColor.YELLOW, Component.text("Alex"));

        assertEquals("§eAlex joined the game", ChatText.legacy(joined));
    }

    @Test
    void numberedArgumentsCanChangeOrder() {
        Component renamed = Component.translatable("multiplayer.player.joined.renamed", Component.text("Alex"), Component.text("Steve"));

        assertEquals("Alex (formerly known as Steve) joined the game", ChatText.legacy(renamed));
    }

    @Test
    void translationsInsideArgumentsAreRenderedToo() {
        Component nested = Component.translatable("chat.type.announcement", Component.text("Server"),
                Component.translatable("multiplayer.player.left", Component.text("Bob")));

        assertEquals("[Server] Bob left the game", ChatText.legacy(nested));
    }

    @Test
    void theServersFallbackIsUsedForUnknownKeys() {
        Component custom = Component.translatable().key("myplugin.hello").fallback("Hello, %s!")
                .arguments(Component.text("Alex")).build();

        assertEquals("Hello, Alex!", ChatText.legacy(custom));
    }

    @Test
    void unknownKeysWithoutFallbackShowTheKeyAndArguments() {
        Component unknown = Component.translatable("myplugin.hello", Component.text("Alex"));

        assertEquals("myplugin.hello Alex", ChatText.legacy(unknown));
    }
}
