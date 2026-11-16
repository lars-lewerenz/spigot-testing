package io.github.larslewerenz.spigottesting;

import io.github.larslewerenz.spigottesting.internal.ServerConditions;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs the annotated test (or every test of the annotated class) only on Minecraft versions in
 * the range, both ends included; elsewhere it is reported as skipped. Leave {@code min} or
 * {@code max} out for an open range, e.g. {@code @EnabledForMinecraftRange(min = "1.13")}.
 * See {@link MinecraftVersion} for how versions compare.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(ServerConditions.class)
public @interface EnabledForMinecraftRange {
    /** The oldest version the test runs on, e.g. {@code "1.13"}; empty for no lower bound. */
    String min() default "";

    /** The newest version the test runs on, e.g. {@code "1.16.5"}; empty for no upper bound. */
    String max() default "";
}
