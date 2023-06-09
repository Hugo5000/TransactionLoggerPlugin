package at.hugob.plugin.transactionlogger.gui;

import at.hugob.plugin.library.gui.GUIHandler;
import at.hugob.plugin.transactionlogger.TransactionLoggerPlugin;
import at.hugob.plugin.transactionlogger.data.EconomyTransaction;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class TransactionsGUI extends GUIHandler<TransactionLoggerPlugin, TransactionsGUIData> {
    private final @NotNull UUID owner;
    private int page;

    /**
     * Creates a GUIHandler
     *
     * @param plugin  The Plugin Instance that owns of this GUI
     * @param guiData The data needed for this GUI to function
     */
    public TransactionsGUI(@NotNull TransactionLoggerPlugin plugin, @NotNull TransactionsGUIData guiData, final @NotNull UUID owner) {
        super(plugin, guiData);
        this.owner = owner;
    }

    @Override
    protected void update() {
        plugin.getTransactionLogManager().get(owner, guiData.transactionSlots.size() * page, guiData.transactionSlots.size()).thenAccept(this::fillTransactions).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "Could not populate Transaction GUI: ", throwable);
            return null;
        });


        setItem(guiData.nextPageSlot, setPlaceHolders(guiData.nextPageItem), this::nextPage);
        if (page > 0) setItem(guiData.previousPageSlot, setPlaceHolders(guiData.previousPageItem), this::previousPage);
        else setItem(guiData.previousPageSlot, guiData.fillerItem);
    }

    private ItemStack setPlaceHolders(ItemStack itemStack) {
        if (itemStack == null) return null;
        itemStack = itemStack.clone();
        final ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta.hasDisplayName())
            itemMeta.displayName(itemMeta.displayName().replaceText(TextReplacementConfig.builder().match("%([^ ]+)%").replacement((matchResult, builder) -> switch (matchResult.group(1)) {
                case "previousPage" -> builder.content(String.valueOf(page));
                case "page" -> builder.content(String.valueOf(page + 1));
                case "nextPage" -> builder.content(String.valueOf(page + 2));
                default -> builder;
            }).build()));
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private boolean previousPage(InventoryClickEvent event) {
        if (event.getClick() == ClickType.LEFT) {
            if (page > 0) --page;
            update();
        }
        return true;
    }

    private boolean nextPage(InventoryClickEvent event) {
        if (event.getClick() == ClickType.LEFT) {
            ++page;
            update();
        }
        return true;
    }

    private void fillTransactions(List<EconomyTransaction> economyTransactions) {
        Iterator<EconomyTransaction> economyTransactionIterator = economyTransactions.listIterator();
        Iterator<Integer> slotIterator = guiData.transactionSlots.listIterator();
        while (slotIterator.hasNext() && economyTransactionIterator.hasNext()) {
            final int slot = slotIterator.next();
            final EconomyTransaction economyTransaction = economyTransactionIterator.next();
            var textReplacementConfig = TextReplacementConfig.builder().match("%([^ ]+)%").replacement((matchResult, builder) -> switch (matchResult.group(1)) {
                case "dateTime" -> builder.content(plugin.getDateTimeFormatter().format(economyTransaction.dateTime()));
                case "from" -> economyTransaction.from() == null ?
                        economyTransaction.consoleContextOr(plugin.getDefaultContext()).displayName() :
                        plugin.getNameManager().getDisplayName(economyTransaction.from());
                case "to" -> economyTransaction.to() == null ?
                        economyTransaction.consoleContextOr(plugin.getDefaultContext()).displayName() :
                        plugin.getNameManager().getDisplayName(economyTransaction.to());
                case "amount" ->
                        builder.content(TransactionLoggerPlugin.decimalFormat.format(economyTransaction.amount()));
                default -> builder;
            }).build();
            final ItemStack itemStack;
            if (economyTransaction.to() == null || economyTransaction.from() == null) {
                itemStack = switch (economyTransaction.consoleContextOr(plugin.getDefaultContext()).name()) {
                    case "Essentials" -> guiData.essentialsTransactionItem;
                    case "EssentialsSell" -> guiData.essentialsSellTransactionItem;
                    case "EssentialsEco" -> guiData.essentialsEcoTransactionItem;
                    case "ChestShop" -> guiData.chestShopTransactionItem;
                    case "BeastWithdraw" -> guiData.beastWithdrawTransactionItem;
                    case "MoneyFromMobs" -> guiData.moneyFromMobsTransactionItem;
                    case "ShopGUIPlus" -> guiData.shopGUIPlusTransactionItem;
                    case "Commands.ecomenu" -> guiData.ecomenuTransactionItem;
                    default -> guiData.unknownTransactionItem;
                };
                if (itemStack != null) {
                    ItemMeta itemMeta = itemStack.getItemMeta();
                    if (owner.equals(economyTransaction.to())) {
                        itemMeta.displayName(guiData.gainedItemName.replaceText(textReplacementConfig));
                        itemMeta.lore(guiData.gainedItemLore.stream().map(c -> c.replaceText(textReplacementConfig)).toList());
                    } else if (owner.equals(economyTransaction.from())) {
                        itemMeta.displayName(guiData.paidItemName.replaceText(textReplacementConfig));
                        itemMeta.lore(guiData.paidItemLore.stream().map(c -> c.replaceText(textReplacementConfig)).toList());
                    }
                    itemStack.setItemMeta(itemMeta);
                }
            } else {
                itemStack = new ItemStack(Material.PLAYER_HEAD);
                ItemMeta itemMeta = itemStack.getItemMeta();
                SkullMeta skullMeta = (SkullMeta) itemMeta;
                if (owner.equals(economyTransaction.to())) {
                    skullMeta.setPlayerProfile(Bukkit.createProfile(economyTransaction.from(), plugin.getNameManager().getName(economyTransaction.from())));
                    itemMeta.displayName(guiData.gainedItemName.replaceText(textReplacementConfig));
                    itemMeta.lore(guiData.gainedItemLore.stream().map(c -> c.replaceText(textReplacementConfig)).toList());
                } else if (owner.equals(economyTransaction.from())) {
                    skullMeta.setPlayerProfile(Bukkit.createProfile(economyTransaction.to(), plugin.getNameManager().getName(economyTransaction.to())));
                    itemMeta.displayName(guiData.paidItemName.replaceText(textReplacementConfig));
                    itemMeta.lore(guiData.paidItemLore.stream().map(c -> c.replaceText(textReplacementConfig)).toList());
                }
                itemStack.setItemMeta(itemMeta);
            }
            setItem(slot, itemStack);
        }
        while (slotIterator.hasNext()) setItem(slotIterator.next(), null);
    }
}
