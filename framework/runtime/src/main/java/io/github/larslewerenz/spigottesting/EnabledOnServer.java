package io.github.larslewerenz.spigottesting;

import io.github.larslewerenz.spigottesting.internal.ServerConditions;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs the annotated test (or every test of the annotated class) only on the given server types;
 * elsewhere it is reported as skipped. For example {@code @EnabledOnServer(ServerType.PAPER)}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(ServerConditions.class)
public @interface EnabledOnServer {
    ServerType[] value();
}
