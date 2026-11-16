package io.github.larslewerenz.greeter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;

/** The names of everyone who has joined before; kept in visitors.yml across restarts. */
final class Visitors {

    private final File file;
    private final Set<String> names = new LinkedHashSet<>();

    Visitors(File file) {
        this.file = file;
        for (String name : YamlConfiguration.loadConfiguration(file).getStringList("names")) {
            names.add(name.toLowerCase(Locale.ROOT));
        }
    }

    /** Records the visit; returns whether it is the first one. */
    boolean visit(String name) {
        return names.add(name.toLowerCase(Locale.ROOT));
    }

    void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("names", new ArrayList<>(names));
        yaml.save(file);
    }
}
