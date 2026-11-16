package io.github.larslewerenz.spigottesting.internal;

import io.github.larslewerenz.spigottesting.AfterRestart;
import java.util.Optional;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;

/**
 * Splits the tests into the ones that run before the restart and the {@link AfterRestart} ones.
 * Only tests are judged; classes are kept and dropped by JUnit once none of their tests is left.
 */
final class RestartPhase implements PostDiscoveryFilter {

    static final String PROPERTY = "spigottesting.phase";
    static final String AFTER_RESTART = "after-restart";

    private final boolean afterRestart;

    RestartPhase(boolean afterRestart) {
        this.afterRestart = afterRestart;
    }

    /** The phase this server run is in, from {@code -Dspigottesting.phase}. */
    static boolean current() {
        return AFTER_RESTART.equals(System.getProperty(PROPERTY));
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        if (!descriptor.getChildren().isEmpty()) {
            return FilterResult.included("decided by its tests");
        }
        boolean annotated = isAfterRestart(descriptor.getSource());
        return annotated == afterRestart
                ? FilterResult.included(afterRestart ? "@AfterRestart" : "runs before the restart")
                : FilterResult.excluded(afterRestart ? "runs before the restart" : "@AfterRestart");
    }

    private static boolean isAfterRestart(Optional<TestSource> source) {
        if (!source.isPresent()) {
            return false;
        }
        if (source.get() instanceof MethodSource) {
            MethodSource method = (MethodSource) source.get();
            return method.getJavaMethod().isAnnotationPresent(AfterRestart.class) || isAfterRestart(method.getJavaClass());
        }
        if (source.get() instanceof ClassSource) {
            return isAfterRestart(((ClassSource) source.get()).getJavaClass());
        }
        return false;
    }

    private static boolean isAfterRestart(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getEnclosingClass()) {
            if (c.isAnnotationPresent(AfterRestart.class)) {
                return true;
            }
        }
        return false;
    }
}
