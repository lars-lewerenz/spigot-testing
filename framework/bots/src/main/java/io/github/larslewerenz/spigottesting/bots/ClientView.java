package io.github.larslewerenz.spigottesting.bots;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.BossBarAction;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntryAction;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ObjectiveAction;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreboardPosition;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.TeamAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoRemovePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundTabListPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundResetScorePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetDisplayObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetObjectivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetScorePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundClearTitlesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetSubtitleTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitleTextPacket;

/**
 * What a client shows besides chat, kept up to date from the packets it receives: the title,
 * the action bar, boss bars, the sidebar scoreboard and the tab list.
 */
final class ClientView {

    private Component title;
    private Component subtitle;
    private Component actionBar;
    private final Map<UUID, Bar> bossBars = new LinkedHashMap<>();
    private final Map<String, Component> objectives = new HashMap<>();
    private String sidebarObjective;
    /** objective -> owner -> score */
    private final Map<String, Map<String, Score>> scores = new HashMap<>();
    private final Map<String, Team> teams = new HashMap<>();
    private Component header;
    private Component footer;
    private final Map<UUID, TabEntry> tabList = new LinkedHashMap<>();

    /** Applies the packet if it changes what the client shows; returns whether it did. */
    synchronized boolean handle(Packet packet) {
        switch (packet) {
            case ClientboundSetTitleTextPacket p -> title = p.getText();
            case ClientboundSetSubtitleTextPacket p -> subtitle = p.getText();
            case ClientboundClearTitlesPacket p -> {
                title = null;
                subtitle = null;
            }
            case ClientboundSetActionBarTextPacket p -> actionBar = p.getText();
            case ClientboundSystemChatPacket p when p.isOverlay() -> actionBar = p.getContent();
            case ClientboundBossEventPacket p -> bossBar(p);
            case ClientboundSetObjectivePacket p -> {
                if (p.getAction() == ObjectiveAction.REMOVE) {
                    objectives.remove(p.getName());
                    scores.remove(p.getName());
                } else {
                    objectives.put(p.getName(), p.getDisplayName());
                }
            }
            case ClientboundSetDisplayObjectivePacket p -> {
                if (p.getPosition() == ScoreboardPosition.SIDEBAR) {
                    sidebarObjective = p.getName().isEmpty() ? null : p.getName();
                }
            }
            case ClientboundSetScorePacket p -> scores.computeIfAbsent(p.getObjective(), o -> new HashMap<>())
                    .put(p.getOwner(), new Score(p.getValue(), p.getDisplay()));
            case ClientboundResetScorePacket p -> {
                if (p.getObjective() == null) {
                    scores.values().forEach(owners -> owners.remove(p.getOwner()));
                } else if (scores.containsKey(p.getObjective())) {
                    scores.get(p.getObjective()).remove(p.getOwner());
                }
            }
            case ClientboundSetPlayerTeamPacket p -> team(p);
            case ClientboundTabListPacket p -> {
                header = p.getHeader();
                footer = p.getFooter();
            }
            case ClientboundPlayerInfoUpdatePacket p -> {
                for (PlayerListEntry entry : p.getEntries()) {
                    TabEntry tab = tabList.computeIfAbsent(entry.getProfileId(), id -> new TabEntry());
                    if (p.getActions().contains(PlayerListEntryAction.ADD_PLAYER) && entry.getProfile() != null) {
                        tab.name = entry.getProfile().getName();
                    }
                    if (p.getActions().contains(PlayerListEntryAction.UPDATE_LISTED)) {
                        tab.listed = entry.isListed();
                    }
                    if (p.getActions().contains(PlayerListEntryAction.UPDATE_DISPLAY_NAME)) {
                        tab.displayName = entry.getDisplayName();
                    }
                }
            }
            case ClientboundPlayerInfoRemovePacket p -> p.getProfileIds().forEach(tabList::remove);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void bossBar(ClientboundBossEventPacket p) {
        if (p.getAction() == BossBarAction.REMOVE) {
            bossBars.remove(p.getUuid());
            return;
        }
        Bar bar = bossBars.computeIfAbsent(p.getUuid(), id -> new Bar());
        if (p.getAction() == BossBarAction.ADD || p.getAction() == BossBarAction.UPDATE_TITLE) {
            bar.title = p.getTitle();
        }
        if (p.getAction() == BossBarAction.ADD || p.getAction() == BossBarAction.UPDATE_HEALTH) {
            bar.progress = p.getHealth();
        }
    }

    private void team(ClientboundSetPlayerTeamPacket p) {
        switch (p.getAction()) {
            case REMOVE -> teams.remove(p.getTeamName());
            case CREATE, UPDATE -> {
                Team team = teams.computeIfAbsent(p.getTeamName(), n -> new Team());
                team.prefix = p.getPlayerPrefix();
                team.suffix = p.getPlayerSuffix();
                if (p.getAction() == TeamAction.CREATE && p.getPlayers() != null) {
                    team.players.addAll(List.of(p.getPlayers()));
                }
            }
            case ADD_PLAYER -> teams.computeIfAbsent(p.getTeamName(), n -> new Team()).players.addAll(List.of(p.getPlayers()));
            case REMOVE_PLAYER -> {
                Team team = teams.get(p.getTeamName());
                if (team != null) {
                    team.players.removeAll(List.of(p.getPlayers()));
                }
            }
        }
    }

    /**
     * The view as lines of {@code key value}, values base64-encoded text with {@code §} color codes:
     * title, subtitle, actionbar, bossbar (progress, then title), sidebar-title, sidebar (score,
     * then text, top to bottom), header, footer, tab (one per listed player).
     */
    synchronized List<String> describe() {
        List<String> lines = new ArrayList<>();
        add(lines, "title", title);
        add(lines, "subtitle", subtitle);
        add(lines, "actionbar", actionBar);
        for (Bar bar : bossBars.values()) {
            lines.add("bossbar " + bar.progress + " " + encode(bar.title));
        }
        if (sidebarObjective != null && objectives.containsKey(sidebarObjective)) {
            add(lines, "sidebar-title", objectives.get(sidebarObjective));
            List<Map.Entry<String, Score>> rows = new ArrayList<>(scores.getOrDefault(sidebarObjective, Map.of()).entrySet());
            // Like the client: highest score first, then by name; at most 15 lines.
            rows.sort(Comparator.<Map.Entry<String, Score>>comparingInt(e -> -e.getValue().value)
                    .thenComparing(Map.Entry::getKey));
            for (Map.Entry<String, Score> row : rows.subList(0, Math.min(15, rows.size()))) {
                Component text = row.getValue().display != null ? row.getValue().display : withTeam(row.getKey());
                lines.add("sidebar " + row.getValue().value + " " + encode(text));
            }
        }
        add(lines, "header", header);
        add(lines, "footer", footer);
        for (TabEntry entry : tabList.values()) {
            if (entry.listed && entry.name != null) {
                lines.add("tab " + encode(entry.displayName != null ? entry.displayName : withTeam(entry.name)));
            }
        }
        return lines;
    }

    /** A name as the client shows it: with the prefix and suffix of its team, if it has one. */
    private Component withTeam(String name) {
        for (Team team : teams.values()) {
            if (team.players.contains(name)) {
                return Component.empty()
                        .append(team.prefix == null ? Component.empty() : team.prefix)
                        .append(Component.text(name))
                        .append(team.suffix == null ? Component.empty() : team.suffix);
            }
        }
        return Component.text(name);
    }

    private static void add(List<String> lines, String key, Component value) {
        if (value != null) {
            lines.add(key + " " + encode(value));
        }
    }

    private static String encode(Component text) {
        String legacy = ChatText.tidy(ChatText.legacy(text == null ? Component.empty() : text));
        return Base64.getEncoder().encodeToString(legacy.getBytes(StandardCharsets.UTF_8));
    }

    private static final class Bar {
        Component title;
        float progress;
    }

    private record Score(int value, Component display) {
    }

    private static final class Team {
        Component prefix;
        Component suffix;
        final Set<String> players = new HashSet<>();
    }

    private static final class TabEntry {
        String name;
        Component displayName;
        boolean listed;
    }
}
