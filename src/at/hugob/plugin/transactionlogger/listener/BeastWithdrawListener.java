package at.hugob.plugin.transactionlogger.listener;

import at.hugob.plugin.transactionlogger.TransactionLoggerPlugin;
import me.mraxetv.beastwithdraw.events.CashRedeemEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public class BeastWithdrawListener implements Listener {
    private final @NotNull TransactionLoggerPlugin plugin;

    public BeastWithdrawListener(final @NotNull TransactionLoggerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCashRedeem(CashRedeemEvent event) {
        plugin.getTransactionLogManager().setContext(null, event.getPlayer().getUniqueId(), BigDecimal.valueOf(event.getCash()),
                plugin.getTransactionLogManager().getContext("BeastWithdraw"));
    }
}
