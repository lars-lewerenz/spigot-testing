package io.github.larslewerenz.spigottesting;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.nio.charset.StandardCharsets;

/**
 * What a bot's client shows besides chat, at one moment: the title, the action bar, boss bars,
 * the sidebar scoreboard and the tab list. Texts carry {@code §} color codes (without codes that
 * have no effect, like trailing ones). Get it with {@link Bot#view()} or {@link Bot#awaitView}.
 */
public final class ClientView {

    private String title;
    private String subtitle;
    private String actionBar;
    private final List<BossBar> bossBars = new ArrayList<>();
    private String sidebarTitle;
    private final List<String> sidebar = new ArrayList<>();
    private final List<Integer> sidebarScores = new ArrayList<>();
    private String tabHeader;
    private String tabFooter;
    private final List<String> tabList = new ArrayList<>();

    private ClientView() {
    }

    /** Parses the bot controller's description: lines of {@code key [number] base64(text)}. */
    static ClientView parse(List<String> lines) {
        ClientView view = new ClientView();
        for (String line : lines) {
            String[] parts = line.split(" ", -1);
            String key = parts[0];
            String text = decode(parts[parts.length - 1]);
            switch (key) {
                case "title": view.title = text; break;
                case "subtitle": view.subtitle = text; break;
                case "actionbar": view.actionBar = text; break;
                case "bossbar": view.bossBars.add(new BossBar(text, Float.parseFloat(parts[1]))); break;
                case "sidebar-title": view.sidebarTitle = text; break;
                case "sidebar":
                    view.sidebar.add(text);
                    view.sidebarScores.add(Integer.parseInt(parts[1]));
                    break;
                case "header": view.tabHeader = text; break;
                case "footer": view.tabFooter = text; break;
                case "tab": view.tabList.add(text); break;
                default: break; // from a newer bot controller: ignore
            }
        }
        return view;
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    /** The last title the server sent, or null. Titles fade on the client; this stays until replaced or cleared. */
    public String title() {
        return title;
    }

    /** The last subtitle the server sent, or null. */
    public String subtitle() {
        return subtitle;
    }

    /** The last action bar text the server sent, or null. */
    public String actionBar() {
        return actionBar;
    }

    /** The boss bars shown, oldest first. */
    public List<BossBar> bossBars() {
        return Collections.unmodifiableList(bossBars);
    }

    /** The title of the sidebar scoreboard, or null if there is no sidebar. */
    public String sidebarTitle() {
        return sidebarTitle;
    }

    /** The sidebar lines, top to bottom (at most 15, like the client shows). */
    public List<String> sidebar() {
        return Collections.unmodifiableList(sidebar);
    }

    /**
     * The score shown next to the first sidebar line equal to {@code line} (the red number), or
     * null if there is no such line.
     */
    public Integer sidebarScore(String line) {
        int index = sidebar.indexOf(line);
        return index < 0 ? null : sidebarScores.get(index);
    }

    /** The text above the tab list, or null. */
    public String tabHeader() {
        return tabHeader;
    }

    /** The text below the tab list, or null. */
    public String tabFooter() {
        return tabFooter;
    }

    /** The names in the tab list as shown (display names, or names with their team prefix and suffix). */
    public List<String> tabList() {
        return Collections.unmodifiableList(tabList);
    }

    @Override
    public String toString() {
        return "ClientView[title=" + title + ", subtitle=" + subtitle + ", actionBar=" + actionBar
                + ", bossBars=" + bossBars + ", sidebarTitle=" + sidebarTitle + ", sidebar=" + sidebar
                + ", tabHeader=" + tabHeader + ", tabFooter=" + tabFooter + ", tabList=" + tabList + "]";
    }

    /** A boss bar on the client's screen. */
    public static final class BossBar {

        private final String title;
        private final float progress;

        BossBar(String title, float progress) {
            this.title = title;
            this.progress = progress;
        }

        /** The text above the bar. */
        public String title() {
            return title;
        }

        /** How full the bar is, from 0 to 1. */
        public float progress() {
            return progress;
        }

        @Override
        public String toString() {
            return title + " (" + progress + ")";
        }
    }
}
