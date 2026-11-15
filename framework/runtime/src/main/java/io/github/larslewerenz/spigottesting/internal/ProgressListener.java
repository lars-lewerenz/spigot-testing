package io.github.larslewerenz.spigottesting.internal;

import java.util.logging.Logger;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Logs one marked line per finished test. The Gradle task picks these lines out of the server
 * output and shows them in the build log.
 */
final class ProgressListener implements TestExecutionListener {

    static final String MARKER = "##spigot-testing## ";

    private final Logger logger;
    private TestPlan plan;

    ProgressListener(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.plan = testPlan;
    }

    @Override
    public void executionSkipped(TestIdentifier id, String reason) {
        logger.info(MARKER + "SKIPPED " + name(id) + (reason == null ? "" : " (" + reason + ")"));
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        TestExecutionResult.Status status = result.getStatus();
        if (!id.isTest() && status == TestExecutionResult.Status.SUCCESSFUL) {
            return;
        }
        String line = MARKER + status.name().replace("SUCCESSFUL", "PASSED") + " " + name(id);
        if (result.getThrowable().isPresent()) {
            line += " -- " + result.getThrowable().get();
        }
        logger.info(line);
    }

    private String name(TestIdentifier id) {
        String parent = plan == null ? "" : plan.getParent(id)
                .filter(p -> p.getSource().isPresent())
                .map(p -> p.getDisplayName() + " > ")
                .orElse("");
        return parent + id.getDisplayName();
    }
}
