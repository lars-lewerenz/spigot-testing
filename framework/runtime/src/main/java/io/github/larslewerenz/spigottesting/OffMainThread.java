package io.github.larslewerenz.spigottesting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs the annotated test method (or every method of the annotated class) on the test thread
 * instead of the server main thread, so the server keeps ticking while the test waits. Use
 * {@link ServerTest#sync(java.util.concurrent.Callable)} for Bukkit calls inside such tests.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface OffMainThread {
}
