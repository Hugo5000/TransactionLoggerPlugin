package at.hugob.plugin.transactionlogger.listener.command;

import at.hugob.plugin.transactionlogger.TransactionLoggerPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public class EssentialsMenuEcoCommandListener implements Listener {
    private final @NotNull TransactionLoggerPlugin plugin;

    public EssentialsMenuEcoCommandListener(@NotNull TransactionLoggerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCommand(ServerCommandEvent event) {
        if (event.getCommand().startsWith("ecomenu take ") || event.getCommand().startsWith("ecomenu give ")) {
            var playerAndAmount = event.getCommand().substring("ecomenu take ".length());
            var playerName = playerAndAmount.substring(0, playerAndAmount.indexOf(' '));
            var player = plugin.getNameManager().getUUID(playerName);
            if (player == null) {
                plugin.getLogger().warning(String.format("EcoMenu Command could not parse playername to uuid \"%s\"", playerName));
                return;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(playerAndAmount.substring(playerName.length() + 1));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning(String.format("EcoMenu Command could not parse amount \"%s\"", playerAndAmount.substring(playerName.length() + 1)));
                return;
            }
            if (event.getCommand().startsWith("ecomenu give "))
                plugin.getTransactionLogManager().setContext(null, player, amount, plugin.getTransactionLogManager().getContext("EssentialsEco"), plugin.getTransactionLogManager().getContext("Commands.ecomenu"));
            else
                plugin.getTransactionLogManager().setContext(player, null, amount, plugin.getTransactionLogManager().getContext("EssentialsEco"), plugin.getTransactionLogManager().getContext("Commands.ecomenu"));
        }
    }
}
