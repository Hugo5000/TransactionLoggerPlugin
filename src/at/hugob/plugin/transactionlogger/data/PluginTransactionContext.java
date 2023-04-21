package at.hugob.plugin.transactionlogger.data;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

public class PluginTransactionContext<Plugin extends org.bukkit.plugin.Plugin> extends ConsoleTransactionContext {
    private final @NotNull Plugin plugin;

    public PluginTransactionContext(@NotNull Plugin plugin, Component displayName) {
        super(plugin.getName(), displayName);
        this.plugin = plugin;
    }
    public PluginTransactionContext(@NotNull Plugin plugin, @NotNull String name, @NotNull Component displayName) {
        super(name, displayName);
        this.plugin = plugin;
    }


    private @NotNull Plugin getPlugin() {
        return plugin;
    }
}
