package at.hugob.plugin.transactionlogger.listener;

import at.hugob.plugin.transactionlogger.TransactionLoggerPlugin;
import at.hugob.plugin.transactionlogger.data.EconomyTransaction;
import net.ess3.api.events.NickChangeEvent;
import net.ess3.api.events.UserBalanceUpdateEvent;
import net.essentialsx.api.v2.events.TransactionEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;

public class EssentialsListener implements Listener {
    private final @NotNull TransactionLoggerPlugin plugin;

    public EssentialsListener(final @NotNull TransactionLoggerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEssentialsBalanceUpdate(UserBalanceUpdateEvent event) {
        if (event.getCause() == UserBalanceUpdateEvent.Cause.COMMAND_PAY) return;
        final BigDecimal amount = event.getNewBalance().subtract(event.getOldBalance());
        final UUID playerFrom = amount.signum() < 0 ? event.getPlayer().getUniqueId() : null;
        final UUID playerTo = amount.signum() < 0 ? null : event.getPlayer().getUniqueId();
        var transaction = new EconomyTransaction(ZonedDateTime.now(UTC), playerFrom, playerTo, amount.abs(), null);
        if(event.getCause() == UserBalanceUpdateEvent.Cause.COMMAND_SELL) transaction.consoleContext(plugin.getTransactionLogManager().getContext("EssentialsSell"));
        else if(event.getCause() == UserBalanceUpdateEvent.Cause.COMMAND_ECO) transaction.consoleContext(plugin.getTransactionLogManager().getContext("EssentialsEco"));

        plugin.getTransactionLogManager().save(transaction);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEssentialsPayTransaction(TransactionEvent event) {
        BigDecimal amount = event.getAmount();
        UUID playerFrom = event.getRequester().getPlayer().getUniqueId();
        UUID playerTo = event.getTarget().getUUID();
        plugin.getTransactionLogManager().save(new EconomyTransaction(ZonedDateTime.now(UTC), playerFrom, playerTo, amount.abs(), plugin.getTransactionLogManager().getContext("Essentials")));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEssentialsNick(NickChangeEvent event) {
        var player = Bukkit.getPlayer(event.getController().getUUID());
        if(player == null) return;
        plugin.getNameManager().saveName(player);
    }


}
