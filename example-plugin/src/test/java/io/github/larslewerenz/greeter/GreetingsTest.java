package io.github.larslewerenz.greeter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;

/** Unit tests: plain JUnit on the build JVM, no server. Run with {@code ./gradlew test}. */
class GreetingsTest {

    @Test
    void replacesNameAndTranslatesColorCodes() {
        assertEquals(ChatColor.GREEN + "Hello, Steve!", Greetings.format("&aHello, %name%!", "Steve"));
    }

    @Test
    void leavesTemplatesWithoutPlaceholdersAlone() {
        assertEquals("Hi", Greetings.format("Hi", "Steve"));
    }

    @Test
    void nightStartsAtDusk() {
        assertFalse(DayLock.isNight(DayLock.MORNING));
        assertFalse(DayLock.isNight(11999));
        assertTrue(DayLock.isNight(12000));
        assertTrue(DayLock.isNight(18000));
    }
}
