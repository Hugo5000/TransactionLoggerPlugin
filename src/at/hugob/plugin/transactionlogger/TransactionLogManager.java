package at.hugob.plugin.transactionlogger;

import at.hugob.plugin.transactionlogger.data.ConsoleTransactionContext;
import at.hugob.plugin.transactionlogger.data.EconomyTransaction;
import at.hugob.plugin.transactionlogger.data.PluginTransactionContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

public class TransactionLogManager {
    private final @NotNull ConcurrentLinkedQueue<EconomyTransaction> bufferedEconomyTransactions = new ConcurrentLinkedQueue<>();
    private final @NotNull TransactionLoggerPlugin plugin;
    private final @NotNull BukkitTask bukkitTask;
    private @NotNull HashMap<String, PluginTransactionContext> pluginTransactionContexts = new HashMap<>();

    public TransactionLogManager(final @NotNull TransactionLoggerPlugin plugin) {
        this.plugin = plugin;
        bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::removeOldBufferedData, 0, 0);
    }

    public void reload() {
        final HashMap<String, PluginTransactionContext> pluginTransactionContexts = new HashMap<>();
        if (Bukkit.getPluginManager().getPlugin("Essentials") != null) {
            pluginTransactionContexts.put("Essentials", new PluginTransactionContext(Bukkit.getPluginManager().getPlugin("Essentials"), plugin.getMessagesConfig().getComponent("console.Essentials")));
            pluginTransactionContexts.put("EssentialsSell", new PluginTransactionContext(Bukkit.getPluginManager().getPlugin("Essentials"), "EssentialsSell", plugin.getMessagesConfig().getComponent("console.EssentialsSell")));
            pluginTransactionContexts.put("EssentialsEco", new PluginTransactionContext(Bukkit.getPluginManager().getPlugin("Essentials"), "EssentialsEco", plugin.getMessagesConfig().getComponent("console.EssentialsEco")));
        }
        if (Bukkit.getPluginManager().getPlugin("ChestShop") != null) {
            pluginTransactionContexts.put("ChestShop", new PluginTransactionContext(Bukkit.getPluginManager().getPlugin("ChestShop"), plugin.getMessagesConfig().getComponent("console.ChestShop")));
        }
        if (Bukkit.getPluginManager().getPlugin("BeastWithdraw") != null) {
            pluginTransactionContexts.put("BeastWithdraw", new PluginTransactionContext(Bukkit.getPluginManager().getPlugin("BeastWithdraw"), plugin.getMessagesConfig().getComponent("console.BeastWithdraw")));
        }
        if (Bukkit.getPluginManager().getPlugin("MoneyFromMobs") != null) {
            pluginTransactionContexts.put("MoneyFromMobs", new PluginTransactionContext(Bukkit.getPluginManager().getPlugin("MoneyFromMobs"), plugin.getMessagesConfig().getComponent("console.MoneyFromMobs")));
        }
        if (Bukkit.getPluginManager().getPlugin("ShopGUIPlus") != null) {
            pluginTransactionContexts.put("ShopGUIPlus", new PluginTransactionContext(Bukkit.getPluginManager().getPlugin("ShopGUIPlus"), plugin.getMessagesConfig().getComponent("console.ShopGUIPlus")));
        }
        this.pluginTransactionContexts = pluginTransactionContexts;

        for (PluginTransactionContext value : pluginTransactionContexts.values()) {
            CompletableFuture.runAsync(() -> plugin.getDatabase().save(value));
        }
    }


    public ConsoleTransactionContext getContext(String name) {
        return pluginTransactionContexts.get(name);
    }

    private void removeOldBufferedData() {
        final ZonedDateTime now = ZonedDateTime.now();
        ArrayList<EconomyTransaction> removed = new ArrayList<>(10);
        for (EconomyTransaction economyTransaction : bufferedEconomyTransactions) {
            if (economyTransaction.dateTime().until(now, ChronoUnit.MILLIS) > 50) {
                if (bufferedEconomyTransactions.remove(economyTransaction)) {
                    removed.add(economyTransaction);
                }
            }
        }
        CompletableFuture.runAsync(() -> {
            for (EconomyTransaction economyTransaction : removed) {
                plugin.getDatabase().save(economyTransaction);
            }
        });
    }


    public void setContext(UUID from, UUID to, BigDecimal amount, ConsoleTransactionContext context) {
        var match = getMatch(transaction -> transaction.amount().compareTo(amount) == 0
                && (from == null || from.equals(transaction.from()))
                && (to == null || to.equals(transaction.to()))
                && transaction.consoleContext() == null);
        if (match != null) match.consoleContext(context);
        // try again once
        else Bukkit.getScheduler().runTask(plugin, () -> {
            var m2 = getMatch(transaction -> transaction.amount().compareTo(amount) == 0
                    && (from == null || from.equals(transaction.from()))
                    && (to == null || to.equals(transaction.to()))
                    && transaction.consoleContext() == null);
            if (m2 != null) m2.consoleContext(context);
        });
    }

    public synchronized void save(EconomyTransaction transaction) {
        if (transaction.from() == null && transaction.to() == null) {
            plugin.getLogger().warning(String.format("Transaction without a player being involved! %s", TransactionLoggerPlugin.decimalFormat.format(transaction.amount())));
            return;
        }
        if (Objects.equals(transaction.from(), transaction.to())) {
            plugin.getLogger().warning(String.format("Not saving Transaction because %s sent himself money! %s", plugin.getNameManager().getName(transaction.from()), TransactionLoggerPlugin.decimalFormat.format(transaction.amount())));
            return;
        }

        if (transaction.from() == null) {
            EconomyTransaction match = getMatch(t -> t.amount().compareTo(transaction.amount()) == 0 && t.to() == null);
            if (match != null) {
                match.to(transaction.to());
                return;
            }
        } else if (transaction.to() == null) {
            EconomyTransaction match = getMatch(t -> t.amount().compareTo(transaction.amount()) == 0 && t.from() == null);
            if (match != null) {
                match.from(transaction.from());
                return;
            }
        }
        bufferedEconomyTransactions.add(transaction);
    }

    private @Nullable EconomyTransaction getMatch(Predicate<EconomyTransaction> condition) {
        for (EconomyTransaction economyTransaction : bufferedEconomyTransactions) {
            if (condition.test(economyTransaction)) {
                return economyTransaction;
            }
        }
        return null;
    }

    public CompletableFuture<List<EconomyTransaction>> get(@Nullable Player player, int offset, int amount) {
        if (player == null) return get((UUID) null, offset, amount);
        return get(player.getUniqueId(), offset, amount);
    }

    public CompletableFuture<List<EconomyTransaction>> get(@Nullable String string, int offset, int amount) {
        if (string == null) return get((UUID) null, offset, amount);
        final UUID uuid = plugin.getNameManager().getUUID(string);
        if (uuid == null) return CompletableFuture.completedFuture(new ArrayList<>(0));
        return get(uuid, offset, amount);
    }

    public CompletableFuture<List<EconomyTransaction>> get(@Nullable UUID uuid, int offset, int amount) {
        return CompletableFuture.supplyAsync(() -> plugin.getDatabase().get(uuid, offset, amount));
    }

    public void disable() {
        bukkitTask.cancel();
        for (EconomyTransaction economyTransaction : bufferedEconomyTransactions) {
            plugin.getDatabase().save(economyTransaction);
        }
        bufferedEconomyTransactions.clear();
    }
}
