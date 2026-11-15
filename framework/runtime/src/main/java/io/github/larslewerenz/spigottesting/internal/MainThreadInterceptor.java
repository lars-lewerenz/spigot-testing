package io.github.larslewerenz.spigottesting.internal;

import io.github.larslewerenz.spigottesting.OffMainThread;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

/**
 * Moves test and lifecycle methods onto the server main thread, unless they are annotated with
 * {@link OffMainThread}. Registered automatically through the Jupiter extension service file.
 */
public final class MainThreadInterceptor implements InvocationInterceptor {

    @Override
    public void interceptBeforeAllMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> context, ExtensionContext extensionContext) throws Throwable {
        proceed(invocation, context.getExecutable(), extensionContext);
    }

    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> context, ExtensionContext extensionContext) throws Throwable {
        proceed(invocation, context.getExecutable(), extensionContext);
    }

    @Override
    public void interceptTestMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> context, ExtensionContext extensionContext) throws Throwable {
        proceed(invocation, context.getExecutable(), extensionContext);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> context, ExtensionContext extensionContext) throws Throwable {
        proceed(invocation, context.getExecutable(), extensionContext);
    }

    @Override
    public <T> T interceptTestFactoryMethod(Invocation<T> invocation,
            ReflectiveInvocationContext<Method> context, ExtensionContext extensionContext) throws Throwable {
        return proceed(invocation, context.getExecutable(), extensionContext);
    }

    @Override
    public void interceptDynamicTest(Invocation<Void> invocation,
            DynamicTestInvocationContext context, ExtensionContext extensionContext) throws Throwable {
        proceed(invocation, null, extensionContext);
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> context, ExtensionContext extensionContext) throws Throwable {
        proceed(invocation, context.getExecutable(), extensionContext);
    }

    @Override
    public void interceptAfterAllMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> context, ExtensionContext extensionContext) throws Throwable {
        proceed(invocation, context.getExecutable(), extensionContext);
    }

    private static <T> T proceed(Invocation<T> invocation, Method method, ExtensionContext context)
            throws Throwable {
        if (isOffMainThread(method) || context.getTestClass().map(MainThreadInterceptor::isOffMainThread).orElse(false)) {
            return invocation.proceed();
        }
        return MainThread.call(invocation::proceed);
    }

    private static boolean isOffMainThread(AnnotatedElement element) {
        return element != null && element.isAnnotationPresent(OffMainThread.class);
    }
}
