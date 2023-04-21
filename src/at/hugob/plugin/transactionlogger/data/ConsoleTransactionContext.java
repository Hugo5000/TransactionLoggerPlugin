package at.hugob.plugin.transactionlogger.data;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
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

    @Override
    public boolean equals(Object obj) {
        if(this == obj) return true;
        if(obj instanceof ConsoleTransactionContext context) {
            return name.equals(context.name);
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ConsoleTransactionContext{");
        sb.append("name='").append(name).append('\'');
        sb.append(", displayName=").append(GsonComponentSerializer.gson().serialize(displayName));
        sb.append('}');
        return sb.toString();
    }
}
