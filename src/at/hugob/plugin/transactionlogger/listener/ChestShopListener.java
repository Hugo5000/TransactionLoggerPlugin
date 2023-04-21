package at.hugob.plugin.transactionlogger.listener;

import at.hugob.plugin.transactionlogger.TransactionLoggerPlugin;
import com.Acrobot.ChestShop.Events.TransactionEvent;
import com.Acrobot.ChestShop.UUIDs.NameManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class ChestShopListener implements Listener {
    private final @NotNull TransactionLoggerPlugin plugin;

    public ChestShopListener(final @NotNull TransactionLoggerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void postTransaction(TransactionEvent event) {
        var client = event.getClient().getUniqueId();
        var owner = NameManager.isAdminShop(event.getOwnerAccount().getUuid()) ? null : event.getOwnerAccount().getUuid();
        if(event.getTransactionType() == TransactionEvent.TransactionType.BUY) {
            plugin.getTransactionLogManager().setContext(client, owner, event.getExactPrice(),
                    plugin.getTransactionLogManager().getContext("ChestShop"));
        } else {
            plugin.getTransactionLogManager().setContext(owner, client, event.getExactPrice(),
                    plugin.getTransactionLogManager().getContext("ChestShop"));
        }
    }
}
