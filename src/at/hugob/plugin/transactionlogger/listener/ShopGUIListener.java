package at.hugob.plugin.transactionlogger.listener;

import at.hugob.plugin.transactionlogger.TransactionLoggerPlugin;
import net.brcdev.shopgui.event.ShopPostTransactionEvent;
import net.brcdev.shopgui.shop.ShopManager;
import net.brcdev.shopgui.shop.ShopTransactionResult;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public class ShopGUIListener implements Listener {
    private final @NotNull TransactionLoggerPlugin plugin;

    public ShopGUIListener(final @NotNull TransactionLoggerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void postTransaction(ShopPostTransactionEvent event) {
        if(!event.getResult().getResult().equals(ShopTransactionResult.ShopTransactionResultType.SUCCESS)) return;
        if(event.getResult().getShopAction().equals(ShopManager.ShopAction.BUY)) {
            plugin.getTransactionLogManager().setContext(event.getResult().getPlayer().getUniqueId(),null, BigDecimal.valueOf(event.getResult().getPrice()),
                    plugin.getTransactionLogManager().getContext("ShopGUIPlus"));
        } else {
            plugin.getTransactionLogManager().setContext(null, event.getResult().getPlayer().getUniqueId(), BigDecimal.valueOf(event.getResult().getPrice()),
                    plugin.getTransactionLogManager().getContext("ShopGUIPlus"));
        }
    }
}
