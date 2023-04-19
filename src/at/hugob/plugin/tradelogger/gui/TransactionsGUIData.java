package at.hugob.plugin.tradelogger.gui;

import at.hugob.plugin.library.gui.GUIData;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TransactionsGUIData extends GUIData {
    public final List<Integer> transactionSlots;

    /**
     * Creates the basic GUIData needed for a GUIHandler to function
     *
     * @param title      The tile for the GUI
     * @param rows       The amount of rows the GUI should have
     * @param fillerItem The Background Filler item, null to leave empty
     */
    public TransactionsGUIData(@NotNull Component title, int rows, @Nullable ItemStack fillerItem, @NotNull List<Integer> transactionSlots) {
        super(title, rows, fillerItem);
        this.transactionSlots = Collections.unmodifiableList(transactionSlots);
    }
}
