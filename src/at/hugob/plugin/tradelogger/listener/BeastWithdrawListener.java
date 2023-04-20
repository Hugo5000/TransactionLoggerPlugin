package at.hugob.plugin.tradelogger.listener;

import at.hugob.plugin.tradelogger.TradeLoggerPlugin;
import me.mraxetv.beastwithdraw.events.CashRedeemEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public class BeastWithdrawListener implements Listener {
    private final @NotNull TradeLoggerPlugin plugin;

    public BeastWithdrawListener(final @NotNull TradeLoggerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCashRedeem(CashRedeemEvent event) {
        plugin.getTransactionLogManager().setContext(null, event.getPlayer().getUniqueId(), BigDecimal.valueOf(event.getCash()),
                plugin.getTransactionLogManager().getContext("BeastWithdraw"));
    }
}
