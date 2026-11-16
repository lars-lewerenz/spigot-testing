package io.github.larslewerenz.greeter;

import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/** The sidebar every player sees: how many players are online and how often /greet was used. */
final class Sidebar {

    private final Scoreboard board;
    private final Objective objective;

    @SuppressWarnings("deprecation") // registerNewObjective(name, criteria): the only form 1.8.8 has
    Sidebar(Scoreboard board) {
        this.board = board;
        this.objective = board.registerNewObjective("greeter", "dummy");
        objective.setDisplayName(ChatColor.GOLD + "Greeter");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    void show(Player player) {
        player.setScoreboard(board);
    }

    void update(int online, int greetings) {
        for (String entry : new ArrayList<>(board.getEntries())) {
            board.resetScores(entry);
        }
        objective.getScore("Online: " + online).setScore(2);
        objective.getScore("Greetings: " + greetings).setScore(1);
    }
}
