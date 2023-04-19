package at.hugob.plugin.tradelogger.listener;

import at.hugob.plugin.tradelogger.TradeLoggerPlugin;
import at.hugob.plugin.tradelogger.data.EconomyTransaction;
import net.ess3.api.events.UserBalanceUpdateEvent;
import net.essentialsx.api.v2.events.TransactionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;

public class EssentialsEconomyListener implements Listener {
    private final @NotNull TradeLoggerPlugin plugin;

    public EssentialsEconomyListener(final @NotNull TradeLoggerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEssentialsBalanceUpdate(UserBalanceUpdateEvent event) {
        if (event.getCause() == UserBalanceUpdateEvent.Cause.COMMAND_PAY) return;

        final BigDecimal amount = event.getNewBalance().subtract(event.getOldBalance());
        final UUID playerFrom = amount.signum() < 0 ? event.getPlayer().getUniqueId() : null;
        final UUID playerTo = amount.signum() < 0 ? null : event.getPlayer().getUniqueId();
        plugin.getTransactionLogManager().save(new EconomyTransaction(playerFrom, playerTo, amount.abs(), ZonedDateTime.now(UTC)));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEssentialsPayTransaction(TransactionEvent event) {
        BigDecimal amount = event.getAmount();
        UUID playerFrom = event.getRequester().getPlayer().getUniqueId();
        UUID playerTo = event.getTarget().getUUID();
        plugin.getTransactionLogManager().save(new EconomyTransaction(playerFrom, playerTo, amount, ZonedDateTime.now(UTC)));
    }
}
