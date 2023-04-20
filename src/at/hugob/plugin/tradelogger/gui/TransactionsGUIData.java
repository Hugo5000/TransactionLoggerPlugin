package at.hugob.plugin.tradelogger.gui;

import at.hugob.plugin.library.gui.GUIData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TransactionsGUIData extends GUIData {
    public final @NotNull List<Integer> transactionSlots;

    public final @NotNull Component gainedItemName;
    public final @NotNull List<Component> gainedItemLore;
    public final @NotNull Component paidItemName;
    public final @NotNull List<Component> paidItemLore;

    public final @Nullable ItemStack nextPageItem;
    public final int nextPageSlot;

    public final @Nullable ItemStack previousPageItem;
    public final int previousPageSlot;

    /**
     * Creates the basic GUIData needed for a GUIHandler to function
     *
     * @param title            The tile for the GUI
     * @param rows             The amount of rows the GUI should have
     * @param fillerItem       The Background Filler item, null to leave empty
     * @param gainedItemName
     * @param gainedItemLore
     * @param paidItemName
     * @param paidItemLore
     * @param nextPageItem
     * @param nextPageSlot
     * @param previousPageItem
     * @param previousPageSlot
     */
    public TransactionsGUIData(@NotNull Component title, int rows, @Nullable ItemStack fillerItem, @NotNull List<Integer> transactionSlots,
                               @NotNull Component gainedItemName, @NotNull List<Component> gainedItemLore,
                               @NotNull Component paidItemName, @NotNull List<Component> paidItemLore,
                               @Nullable ItemStack nextPageItem, int nextPageSlot,
                               @Nullable ItemStack previousPageItem, int previousPageSlot) {
        super(title, rows, fillerItem);
        this.transactionSlots = Collections.unmodifiableList(transactionSlots);
        this.gainedItemName = Component.empty().color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false).append(gainedItemName);
        this.gainedItemLore = gainedItemLore.stream().map(Component.empty().color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)::append).map(Component::asComponent).toList();
        this.paidItemName = Component.empty().color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false).append(paidItemName);
        this.paidItemLore = paidItemLore.stream().map(Component.empty().color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)::append).map(Component::asComponent).toList();
        this.nextPageItem = nextPageItem;
        this.nextPageSlot = nextPageSlot;
        this.previousPageItem = previousPageItem;
        this.previousPageSlot = previousPageSlot;
    }
}
