package at.hugob.plugin.transactionlogger.listener;

import at.hugob.plugin.transactionlogger.TransactionLoggerPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerNameListener implements Listener {
    private final @NotNull TransactionLoggerPlugin plugin;

    public PlayerNameListener(final @NotNull TransactionLoggerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getNameManager().saveName(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLeave(PlayerQuitEvent event) {
        plugin.getNameManager().saveName(event.getPlayer());
    }
}
