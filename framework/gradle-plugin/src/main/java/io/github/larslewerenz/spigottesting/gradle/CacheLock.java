package io.github.larslewerenz.spigottesting.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes work on one file of the shared cache (~/.gradle/caches/spigot-testing): between
 * tasks of the same build (several plugin projects testing the same server version) and between
 * Gradle processes (parallel builds on one machine). File locks alone don't exclude threads of
 * the same JVM, so an in-process lock comes first.
 */
final class CacheLock {

    private static final Map<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    interface Work {
        void run() throws IOException;
    }

    private CacheLock() {
    }

    /** Runs {@code work} while holding the lock for {@code target}. */
    static void withLock(File target, Work work) {
        ReentrantLock lock = LOCKS.computeIfAbsent(target.getAbsolutePath(), path -> new ReentrantLock());
        lock.lock();
        try {
            File lockFile = new File(target.getPath() + ".lock");
            Files.createDirectories(lockFile.getParentFile().toPath());
            try (FileChannel channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                work.run();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            lock.unlock();
        }
    }
}
