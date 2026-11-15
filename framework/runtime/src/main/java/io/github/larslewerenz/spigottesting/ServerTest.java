package io.github.larslewerenz.spigottesting;

import io.github.larslewerenz.spigottesting.internal.MainThread;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

/**
 * Helpers for tests that run inside a real Spigot server.
 *
 * <p>Test methods run on the server main thread by default, so they can use the Bukkit API
 * directly. Tests that need to let the server tick (scheduled tasks, delayed effects) are
 * annotated with {@link OffMainThread} and use {@link #sync(Callable)} and {@link #awaitTicks(int)}.
 */
public final class ServerTest {

    private ServerTest() {
    }

    /** Runs {@code task} on the server main thread, waits for it and returns its result. */
    public static <T> T sync(Callable<T> task) {
        return MainThread.call(task::call);
    }

    /** Runs {@code task} on the server main thread and waits for it. */
    public static void sync(Runnable task) {
        MainThread.call(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Blocks the test until the server has ticked {@code ticks} times. Only callable from tests
     * annotated with {@link OffMainThread}: on the main thread the server could never tick.
     */
    public static void awaitTicks(int ticks) {
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "awaitTicks() blocks the server main thread; annotate the test with @OffMainThread");
        }
        CountDownLatch latch = new CountDownLatch(1);
        Bukkit.getScheduler().runTaskLater(MainThread.plugin(), latch::countDown, ticks);
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for ticks", e);
        }
    }

    /**
     * Dispatches a command as the console (without the leading slash) and records every message
     * sent back to the sender.
     */
    public static CommandResult dispatchCommand(String commandLine) {
        return sync(() -> {
            List<String> messages = new ArrayList<>();
            ConsoleCommandSender sender = recordingConsole(messages);
            boolean success = Bukkit.dispatchCommand(sender, commandLine);
            return new CommandResult(success, messages);
        });
    }

    /**
     * A console sender that behaves like the real console but records messages. It is a dynamic
     * proxy so it implements whatever methods {@link ConsoleCommandSender} has in the running
     * server version.
     */
    private static ConsoleCommandSender recordingConsole(List<String> messages) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ((name.equals("sendMessage") || name.equals("sendRawMessage")) && args != null && args.length > 0) {
                Object message = args[args.length - 1];
                if (message instanceof String) {
                    messages.add((String) message);
                    return null;
                }
                if (message instanceof String[]) {
                    Collections.addAll(messages, (String[]) message);
                    return null;
                }
            }
            switch (name) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "RecordingConsoleCommandSender";
                default:
                    try {
                        return method.invoke(console, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
            }
        };
        return (ConsoleCommandSender) Proxy.newProxyInstance(
                ServerTest.class.getClassLoader(), new Class<?>[] {ConsoleCommandSender.class}, handler);
    }
}
