package io.github.larslewerenz.spigottesting;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs the annotated test (or every test of the annotated class) after the server has been
 * stopped and started again, to check what a plugin keeps across a restart: saved data, worlds,
 * configs. All other tests run first; then the server shuts down normally (plugins are disabled
 * and save their data), starts again with the same server folder, and runs these tests.
 *
 * <p>Pass data between the two runs through the server itself, e.g. a player name only these
 * tests use.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AfterRestart {
}
