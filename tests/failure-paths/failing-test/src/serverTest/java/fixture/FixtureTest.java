package fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FixtureTest {

    @Test
    void passes() {
        assertEquals(2, 1 + 1);
    }

    @Test
    void failsOnPurpose() {
        assertEquals("expected", "actual", "this test fails on purpose");
    }
}
