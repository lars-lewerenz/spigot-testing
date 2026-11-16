package fixture;

import org.junit.jupiter.api.Test;

class FixtureTest {

    @Test
    void neverRuns() {
        // The plugin fails in onEnable, so the tests must not run.
    }
}
