package io.github.larslewerenz.spigottesting.internal;

import io.github.larslewerenz.spigottesting.EnabledForMinecraftRange;
import io.github.larslewerenz.spigottesting.EnabledOnServer;
import io.github.larslewerenz.spigottesting.MinecraftVersion;
import io.github.larslewerenz.spigottesting.ServerTest;
import io.github.larslewerenz.spigottesting.ServerType;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/** Evaluates {@link EnabledOnServer} and {@link EnabledForMinecraftRange}. */
public final class ServerConditions implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<AnnotatedElement> element = context.getElement();
        if (!element.isPresent()) {
            return ConditionEvaluationResult.enabled("no element");
        }
        Optional<EnabledOnServer> types = AnnotationSupport.findAnnotation(element.get(), EnabledOnServer.class);
        if (types.isPresent()) {
            ServerType current = ServerTest.serverType();
            if (!Arrays.asList(types.get().value()).contains(current)) {
                return ConditionEvaluationResult.disabled("only on " + Arrays.toString(types.get().value()) + ", not on " + current);
            }
        }
        Optional<EnabledForMinecraftRange> range = AnnotationSupport.findAnnotation(element.get(), EnabledForMinecraftRange.class);
        if (range.isPresent()) {
            MinecraftVersion current = MinecraftVersion.parse(ServerTest.minecraftVersion());
            String min = range.get().min();
            String max = range.get().max();
            if ((!min.isEmpty() && current.compareTo(MinecraftVersion.parse(min)) < 0)
                    || (!max.isEmpty() && current.compareTo(MinecraftVersion.parse(max)) > 0)) {
                String wanted = min.isEmpty() ? "<= " + max : max.isEmpty() ? ">= " + min : min + " to " + max;
                return ConditionEvaluationResult.disabled("only on Minecraft " + wanted + ", not on " + current);
            }
        }
        return ConditionEvaluationResult.enabled("server matches");
    }
}
