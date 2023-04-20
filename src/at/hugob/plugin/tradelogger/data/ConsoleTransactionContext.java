package at.hugob.plugin.tradelogger.data;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

public class ConsoleTransactionContext {
    private final @NotNull String name;
    private final @NotNull Component displayName;

    public ConsoleTransactionContext(@NotNull String name, @NotNull Component displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    public @NotNull String name() {
        return name;
    }

    public @NotNull Component displayName() {
        return displayName;
    }
}
