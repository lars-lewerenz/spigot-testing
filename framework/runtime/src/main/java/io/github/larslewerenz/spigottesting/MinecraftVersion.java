package io.github.larslewerenz.spigottesting;

import java.util.Arrays;

/**
 * A Minecraft version such as {@code 1.8.8}, {@code 1.21.11} or {@code 26.3}, compared number by
 * number ({@code 1.8.8 < 1.13 < 1.21.11 < 26.1}). Missing parts count as 0 ({@code 1.13 = 1.13.0}).
 */
public final class MinecraftVersion implements Comparable<MinecraftVersion> {

    private final String text;
    private final int[] parts;

    private MinecraftVersion(String text, int[] parts) {
        this.text = text;
        this.parts = parts;
    }

    /** Parses a version like {@code 1.8.8}; anything after a {@code -} (e.g. {@code -R0.1-SNAPSHOT}) is ignored. */
    public static MinecraftVersion parse(String version) {
        String plain = version.trim();
        int dash = plain.indexOf('-');
        if (dash >= 0) {
            plain = plain.substring(0, dash);
        }
        String[] pieces = plain.split("\\.", -1);
        int[] parts = new int[pieces.length];
        try {
            for (int i = 0; i < pieces.length; i++) {
                parts[i] = Integer.parseInt(pieces[i]);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a Minecraft version: '" + version + "'", e);
        }
        return new MinecraftVersion(plain, parts);
    }

    public boolean isAtLeast(MinecraftVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        for (int i = 0; i < Math.max(parts.length, other.parts.length); i++) {
            int a = i < parts.length ? parts[i] : 0;
            int b = i < other.parts.length ? other.parts[i] : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MinecraftVersion && compareTo((MinecraftVersion) o) == 0;
    }

    @Override
    public int hashCode() {
        // Equal versions may differ in trailing zeros (1.13 = 1.13.0), so hash without them.
        int length = parts.length;
        while (length > 0 && parts[length - 1] == 0) {
            length--;
        }
        return Arrays.hashCode(Arrays.copyOf(parts, length));
    }

    @Override
    public String toString() {
        return text;
    }
}
