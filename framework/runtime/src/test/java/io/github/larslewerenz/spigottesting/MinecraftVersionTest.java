package io.github.larslewerenz.spigottesting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MinecraftVersionTest {

    @Test
    void versionsCompareNumberByNumber() {
        List<String> sorted = Arrays.asList("26.1", "1.21.11", "1.8.8", "1.13", "1.21.4", "1.12.2", "26.3")
                .stream()
                .map(MinecraftVersion::parse)
                .sorted()
                .map(MinecraftVersion::toString)
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("1.8.8", "1.12.2", "1.13", "1.21.4", "1.21.11", "26.1", "26.3"), sorted);
    }

    @Test
    void missingPartsCountAsZero() {
        assertEquals(MinecraftVersion.parse("1.13"), MinecraftVersion.parse("1.13.0"));
        assertEquals(MinecraftVersion.parse("1.13").hashCode(), MinecraftVersion.parse("1.13.0").hashCode());
        assertTrue(MinecraftVersion.parse("1.13.0").isAtLeast(MinecraftVersion.parse("1.13")));
        assertFalse(MinecraftVersion.parse("1.12.2").isAtLeast(MinecraftVersion.parse("1.13")));
    }

    @Test
    void bukkitVersionSuffixesAreIgnored() {
        assertEquals("1.8.8", MinecraftVersion.parse("1.8.8-R0.1-SNAPSHOT").toString());
        assertEquals(MinecraftVersion.parse("1.21.11"), MinecraftVersion.parse(" 1.21.11-R0.1-SNAPSHOT "));
    }

    @Test
    void nonsenseIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse("latest"));
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse("1..2"));
        assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse(""));
    }
}
