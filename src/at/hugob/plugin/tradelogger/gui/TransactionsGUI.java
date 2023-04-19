package at.hugob.plugin.tradelogger.gui;

import at.hugob.plugin.library.gui.GUIHandler;
import at.hugob.plugin.tradelogger.TradeLoggerPlugin;
import at.hugob.plugin.tradelogger.data.EconomyTransaction;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class TransactionsGUI extends GUIHandler<TradeLoggerPlugin, TransactionsGUIData> {
    private UUID owner;
    private int page;

    /**
     * Creates a GUIHandler
     *
     * @param plugin  The Plugin Instance that owns of this GUI
     * @param guiData The data needed for this GUI to function
     */
    protected TransactionsGUI(@NotNull TradeLoggerPlugin plugin, @NotNull TransactionsGUIData guiData) {
        super(plugin, guiData);
    }

    @Override
    protected void update() {
        plugin.getTransactionLogManager().get(owner, guiData.transactionSlots.size() * page, guiData.transactionSlots.size()).thenAccept(economyTransactions -> {

        });
    }

    private void fillTransactions(List<EconomyTransaction> economyTransactions) {
        Iterator<EconomyTransaction> economyTransactionIterator = economyTransactions.listIterator();
        Iterator<Integer> slotIterator = guiData.transactionSlots.listIterator();
        while (slotIterator.hasNext() && economyTransactionIterator.hasNext()) {
            final int slot = slotIterator.next();
            final EconomyTransaction economyTransaction = economyTransactionIterator.next();
            ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (owner.equals(economyTransaction.from()) && owner.equals(economyTransaction.to())) {
                //TODO do gui I guess
            } else if (owner.equals(economyTransaction.to())) {

            } else if (owner.equals(economyTransaction.from())) {

            } else {
                setItem(slot, null);
            }
        }
    }
}
