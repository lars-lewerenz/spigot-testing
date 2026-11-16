package io.github.larslewerenz.greeter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.larslewerenz.spigottesting.EnabledForMinecraftRange;
import io.github.larslewerenz.spigottesting.ServerTest;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Tests that only make sense on some Minecraft versions: 1.13 renamed most materials
 * ("the flattening"), so code that looks materials up by name behaves differently before and after.
 * Tests outside their range are reported as skipped.
 */
class VersionConditionsTest {

    @Test
    @EnabledForMinecraftRange(max = "1.12.2")
    void beforeTheFlatteningWoolIsOneMaterial() {
        assertNotNull(Material.matchMaterial("WOOL"));
        assertNull(Material.matchMaterial("WHITE_WOOL"));
        assertFalse(ServerTest.isMinecraftAtLeast("1.13"));
    }

    @Test
    @EnabledForMinecraftRange(min = "1.13")
    void sinceTheFlatteningEveryColourIsItsOwnMaterial() {
        assertNotNull(Material.matchMaterial("WHITE_WOOL"));
        assertNull(Material.matchMaterial("WOOL"));
        assertTrue(ServerTest.isMinecraftAtLeast("1.13"));
    }
}
