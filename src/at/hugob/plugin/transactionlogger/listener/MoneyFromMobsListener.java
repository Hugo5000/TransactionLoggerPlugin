package at.hugob.plugin.transactionlogger.listener;

import at.hugob.plugin.transactionlogger.TransactionLoggerPlugin;
import me.chocolf.moneyfrommobs.api.event.GiveMoneyEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public class MoneyFromMobsListener implements Listener {
    private final @NotNull TransactionLoggerPlugin plugin;

    public MoneyFromMobsListener(final @NotNull TransactionLoggerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoneyFromMobsGiveMoney(GiveMoneyEvent event) {
        plugin.getTransactionLogManager().setContext(null, event.getPlayer().getUniqueId(), BigDecimal.valueOf(event.getAmount()),
                plugin.getTransactionLogManager().getContext("MoneyFromMobs"));
    }
}
