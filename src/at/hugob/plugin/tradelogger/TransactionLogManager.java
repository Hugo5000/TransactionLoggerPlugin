package at.hugob.plugin.tradelogger;

import at.hugob.plugin.tradelogger.data.EconomyTransaction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TransactionLogManager {
    private final @NotNull ConcurrentLinkedQueue<EconomyTransaction> notMatched = new ConcurrentLinkedQueue<>();
    private final @NotNull TradeLoggerPlugin plugin;
    private final @NotNull BukkitTask bukkitTask;

    public TransactionLogManager(final @NotNull TradeLoggerPlugin plugin) {
        this.plugin = plugin;
        bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::removeOldUnmatchedData, 0, 0);
    }

    private void removeOldUnmatchedData() {
        final ZonedDateTime now = ZonedDateTime.now();
        ArrayList<EconomyTransaction> removed = new ArrayList<>();
        for (EconomyTransaction economyTransaction : notMatched) {
            if (economyTransaction.dateTime().until(now, ChronoUnit.MILLIS) > 50) {
                if (notMatched.remove(economyTransaction)) {
                    removed.add(economyTransaction);
                }
            }
        }
        CompletableFuture.runAsync(() -> {
            for (EconomyTransaction economyTransaction : removed) {
                plugin.getDatabase().save(economyTransaction);
                plugin.getLogger().info("Saving" + TradeLoggerPlugin.decimalFormat.format(economyTransaction.amount()));
            }
        });
    }

    public synchronized void save(EconomyTransaction transaction) {
        if (transaction.from() == null && transaction.to() == null) {
            plugin.getLogger().warning(String.format("Transaction without a player being involved! %s", TradeLoggerPlugin.decimalFormat.format(transaction.amount())));
            return;
        }
        if (Objects.equals(transaction.from(), transaction.to())) {
            plugin.getLogger().info(String.format("Not saving Transaction because %s sent himself money! %s", plugin.getNameManager().getName(transaction.from()), TradeLoggerPlugin.decimalFormat.format(transaction.amount())));
            return;
        }

        if (transaction.from() == null) {
            EconomyTransaction match = null;
            for (EconomyTransaction economyTransaction : notMatched) {
                if (economyTransaction.amount().equals(transaction.amount()) && economyTransaction.to() == null) {
                    match = economyTransaction;
                    break;
                }
            }
            if (match == null || !notMatched.remove(match)) {
                notMatched.add(transaction);
                return;
            }
            transaction = new EconomyTransaction(match.from(), transaction.to(), transaction.amount(), transaction.dateTime());
        } else if (transaction.to() == null) {
            EconomyTransaction match = null;
            for (EconomyTransaction economyTransaction : notMatched) {
                if (economyTransaction.amount().equals(transaction.amount()) && economyTransaction.from() == null) {
                    match = economyTransaction;
                    break;
                }
            }
            if (match == null || !notMatched.remove(match)) {
                notMatched.add(transaction);
                return;
            }
            transaction = new EconomyTransaction(transaction.from(), match.to(), transaction.amount(), transaction.dateTime());
        }
        final EconomyTransaction saving = transaction;
        CompletableFuture.runAsync(() -> plugin.getDatabase().save(saving));
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
        for (EconomyTransaction economyTransaction : notMatched) {
            plugin.getDatabase().save(economyTransaction);
        }
        notMatched.clear();
    }
}
