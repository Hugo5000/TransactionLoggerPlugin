package at.hugob.plugin.tradelogger;

import at.hugob.plugin.library.config.ConfigUtils;
import at.hugob.plugin.library.config.YamlFileConfig;
import at.hugob.plugin.tradelogger.gui.TransactionsGUI;
import at.hugob.plugin.tradelogger.gui.TransactionsGUIData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

public class GUIManager {
    private final TradeLoggerPlugin plugin;
    private final YamlFileConfig transactionGUIConfig;

    private TransactionsGUIData transactionsGUIData = new TransactionsGUIData(
            Component.empty(), 6, null, Collections.EMPTY_LIST,
            Component.empty(), Collections.EMPTY_LIST,
            Component.empty(), Collections.EMPTY_LIST,
            null, 0,
            null, 0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    public GUIManager(TradeLoggerPlugin plugin) {
        this.plugin = plugin;
        this.transactionGUIConfig = new YamlFileConfig(plugin, "gui.yml");
    }

    public void reload() {
        transactionGUIConfig.reload();

        transactionsGUIData = new TransactionsGUIData(
                transactionGUIConfig.getComponent("title"),
                transactionGUIConfig.getInt("rows"),
                ConfigUtils.getItemStack(transactionGUIConfig, "filler-item"),
                transactionGUIConfig.getIntegerList("transaction-slots"),
                transactionGUIConfig.getComponent("gained-item.name"),
                transactionGUIConfig.getStringList("gained-item.lore").stream().map(LegacyComponentSerializer.legacyAmpersand()::deserialize).collect(Collectors.toList()),
                transactionGUIConfig.getComponent("paid-item.name"),
                transactionGUIConfig.getStringList("paid-item.lore").stream().map(LegacyComponentSerializer.legacyAmpersand()::deserialize).collect(Collectors.toList()),
                ConfigUtils.getItemStack(transactionGUIConfig, "next-page.item"),
                transactionGUIConfig.getInt("next-page.slot"),
                ConfigUtils.getItemStack(transactionGUIConfig, "previous-page.item"),
                transactionGUIConfig.getInt("previous-page.slot"),
                ConfigUtils.getItemStack(transactionGUIConfig, "console.Unknown"),
                ConfigUtils.getItemStack(transactionGUIConfig, "console.Essentials"),
                ConfigUtils.getItemStack(transactionGUIConfig, "console.EssentialsSell"),
                ConfigUtils.getItemStack(transactionGUIConfig, "console.EssentialsEco"),
                ConfigUtils.getItemStack(transactionGUIConfig, "console.ChestShop"),
                ConfigUtils.getItemStack(transactionGUIConfig, "console.BeastWithdraw"),
                ConfigUtils.getItemStack(transactionGUIConfig, "console.MoneyFromMobs"),
                ConfigUtils.getItemStack(transactionGUIConfig, "console.ShopGUIPlus")
        );
    }

    public void openTransactionGUI(Player player, UUID owner) {
        new TransactionsGUI(plugin, transactionsGUIData, owner).open(player);
    }
}
