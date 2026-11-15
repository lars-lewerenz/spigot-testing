package io.github.larslewerenz.greeter;

final class DayLock {

    static final long MORNING = 1000;
    private static final long DUSK = 12000;

    private DayLock() {
    }

    /** {@code time} is the world time in ticks within the day (0 - 23999). */
    static boolean isNight(long time) {
        return time >= DUSK;
    }
}
