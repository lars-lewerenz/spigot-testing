package io.github.larslewerenz.spigottesting;

import java.util.Collections;
import java.util.List;

/** Outcome of {@link ServerTest#dispatchCommand(String)}. */
public final class CommandResult {

    private final boolean success;
    private final List<String> messages;

    CommandResult(boolean success, List<String> messages) {
        this.success = success;
        this.messages = Collections.unmodifiableList(messages);
    }

    /** What {@code Bukkit.dispatchCommand} returned: {@code false} for unknown commands. */
    public boolean success() {
        return success;
    }

    /** Messages sent to the command sender, in order, including color codes. */
    public List<String> messages() {
        return messages;
    }

    @Override
    public String toString() {
        return "CommandResult{success=" + success + ", messages=" + messages + '}';
    }
}
