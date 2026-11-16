package io.github.larslewerenz.spigottesting.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarColor;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarDivision;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.CollisionRule;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.NameTagVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ObjectiveAction;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreType;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreboardPosition;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.TeamColor;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundResetScorePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetDisplayObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetScorePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundClearTitlesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetSubtitleTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitleTextPacket;
import org.junit.jupiter.api.Test;

class ClientViewTest {

    private final ClientView view = new ClientView();

    @Test
    void titlesStayUntilCleared() {
        view.handle(new ClientboundSetTitleTextPacket(Component.text("Welcome", NamedTextColor.GOLD)));
        view.handle(new ClientboundSetSubtitleTextPacket(Component.text("to the server")));

        assertEquals(List.of("title §6Welcome", "subtitle to the server"), describe());

        view.handle(new ClientboundClearTitlesPacket(false));

        assertEquals(List.of(), describe());
    }

    @Test
    void overlayChatIsTheActionBar() {
        assertEquals(true, view.handle(new ClientboundSystemChatPacket(Component.text("+5 coins"), true)));
        assertFalse(view.handle(new ClientboundSystemChatPacket(Component.text("a chat message"), false)));

        assertEquals(List.of("actionbar +5 coins"), describe());
    }

    @Test
    void bossBarsAreAddedUpdatedAndRemoved() {
        UUID id = UUID.randomUUID();
        view.handle(new ClientboundBossEventPacket(id, Component.text("Event"), 1.0f, BossBarColor.PINK,
                BossBarDivision.NONE, false, false, false));
        view.handle(new ClientboundBossEventPacket(id, 0.25f));

        assertEquals(List.of("bossbar 0.25 Event"), describe());

        view.handle(new ClientboundBossEventPacket(id));

        assertEquals(List.of(), describe());
    }

    @Test
    void sidebarLinesAreSortedByScoreAndUseTeamPrefixes() {
        view.handle(new ClientboundSetObjectivePacket("side", ObjectiveAction.ADD, Component.text("Stats", NamedTextColor.GOLD),
                ScoreType.INTEGER, null));
        view.handle(new ClientboundSetDisplayObjectivePacket(ScoreboardPosition.SIDEBAR, "side"));
        view.handle(new ClientboundSetScorePacket("Online: 2", "side", 2));
        // The usual trick for lines that change: a color code as the entry, the text in a team prefix.
        view.handle(new ClientboundSetPlayerTeamPacket("line1", Component.empty(), Component.text("Kills: 3"), Component.empty(),
                true, false, NameTagVisibility.ALWAYS, CollisionRule.ALWAYS, TeamColor.WHITE, new String[] {"§1"}));
        view.handle(new ClientboundSetScorePacket("§1", "side", 1));
        view.handle(new ClientboundSetScorePacket("other objective", "below", 9));

        assertEquals(List.of("sidebar-title §6Stats", "sidebar 2 Online: 2", "sidebar 1 Kills: 3"), describe());

        view.handle(new ClientboundResetScorePacket("Online: 2", "side"));

        assertEquals(List.of("sidebar-title §6Stats", "sidebar 1 Kills: 3"), describe());
    }

    @Test
    void noSidebarOnceItsObjectiveIsRemoved() {
        view.handle(new ClientboundSetObjectivePacket("side", ObjectiveAction.ADD, Component.text("Stats"), ScoreType.INTEGER, null));
        view.handle(new ClientboundSetDisplayObjectivePacket(ScoreboardPosition.SIDEBAR, "side"));
        view.handle(new ClientboundSetScorePacket("line", "side", 1));

        view.handle(new ClientboundSetObjectivePacket("side"));

        assertEquals(List.of(), describe());
    }

    @Test
    void colorCodesWithoutEffectAreDropped() {
        assertEquals("§aKills: 3", ChatText.tidy("§f§aKills: 3§0§r"));
        assertEquals("§a§lBold", ChatText.tidy("§a§lBold"));
        assertEquals("§cRed", ChatText.tidy("§l§cRed"));
        assertEquals("", ChatText.tidy("§1§r"));
    }

    /** The view's lines with the base64 text decoded. */
    private List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (String line : view.describe()) {
            int textStart = line.lastIndexOf(' ') + 1;
            lines.add(line.substring(0, textStart)
                    + new String(Base64.getDecoder().decode(line.substring(textStart)), StandardCharsets.UTF_8));
        }
        return lines;
    }
}
