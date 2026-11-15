package io.github.larslewerenz.spigottesting.gradle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Picking the Java version for a server from Spigot's and PaperMC's metadata. */
class JavaVersionTest {

    @ParameterizedTest(name = "Spigot javaVersions {0} -> Java {1}")
    @CsvSource(delimiter = ';', value = {
            // real ranges from hub.spigotmc.org/versions/<version>.json (class file versions)
            "[52, 56]; 8",  // 1.13.2
            "[52, 60]; 8",  // 1.16.5
            "[60, 61]; 17", // 1.17.1: Java 16-17, prefer the LTS
            "[61, 62]; 17", // 1.18.2
            "[65, 66]; 21", // 1.20.6
            "[65, 68]; 21", // 1.21.4
            "[69, 70]; 25", // 26.1.2
            "[69]; 25",     // only a minimum
    })
    void spigotPicksLowestLtsInRange(String range, int java) {
        assertEquals(java, SpigotVersions.javaFromMetadata("{\"name\": \"4458\", \"javaVersions\": " + range + "}"));
    }

    @Test
    void spigotWithoutRangeUsesJava8() {
        // 1.8 - 1.11 have no javaVersions
        assertEquals(8, SpigotVersions.javaFromMetadata("{\"name\": \"582b\", \"refs\": {}}"));
    }

    @Test
    void rangeWithoutLtsUsesItsMinimum() {
        assertEquals(12, SpigotVersions.pickJava(12, 13));
    }

    @ParameterizedTest(name = "Paper minimum Java {0} -> Java {1}")
    @CsvSource({"8, 8", "16, 17", "17, 17", "21, 21", "25, 25"})
    void paperPicksLowestLtsAtOrAboveMinimum(int minimum, int java) {
        String json = "{\"version\":{\"id\":\"x\",\"support\":{\"status\":\"SUPPORTED\"},"
                + "\"java\":{\"version\":{\"minimum\":" + minimum + "},\"flags\":{\"recommended\":[]}}}}";
        assertEquals(java, PaperVersions.javaFromMetadata(json, "x"));
    }

    @Test
    void paperBuildDownload() {
        String json = "{\"id\":253,\"channel\":\"STABLE\",\"downloads\":{\"server:default\":{\"name\":\"paper-26.3-253.jar\","
                + "\"checksums\":{\"sha256\":\"f9df8e19f4bdee97dbded48095391ea69c64342a4e19624db4f5e0d0136dfe0b\"},\"size\":55296155,"
                + "\"url\":\"https://fill-data.papermc.io/v1/objects/f9df/paper-26.3-253.jar\"}}}";
        assertArrayEquals(new String[] {
                "https://fill-data.papermc.io/v1/objects/f9df/paper-26.3-253.jar",
                "f9df8e19f4bdee97dbded48095391ea69c64342a4e19624db4f5e0d0136dfe0b"},
                PaperVersions.downloadFromBuild(json, "26.3"));
    }

    @Test
    void paperBuildWithoutDownloadFails() {
        assertThrows(GradleException.class, () -> PaperVersions.downloadFromBuild("{\"id\":1,\"downloads\":{}}", "1.0"));
    }
}
