package at.hugob.plugin.tradelogger.database;

import at.hugob.plugin.tradelogger.data.EconomyTransaction;
import at.hugob.plugin.tradelogger.data.PlayerName;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public interface ITradeLogDatabase {
    void saveNames(final @NotNull PlayerName playerName);

    @NotNull List<PlayerName> getPlayerNames();

    /**
     * Saves the transaction in the database
     *
     * @param transaction the Transaction to save
     */
    void save(final @NotNull EconomyTransaction transaction);

    /**
     * Gets up to {@code amount} transactions in chronological order with a specific player involved, skipping the first {@code offset} transactions
     *
     * @param player The player to get the transactions of
     * @param offset How many transactions should be skipped
     * @param amount The amount of transactions to get
     * @return A list of Transactions
     */
    @NotNull List<@NotNull EconomyTransaction> get(final UUID player, int offset, int amount);

    /**
     * Gets up to {@code amount} transactions in chronological order where a specific player is the one who lost money, skipping the first {@code offset} transactions
     *
     * @param player
     * @param offset
     * @param amount
     * @return
     */
    @NotNull List<@NotNull EconomyTransaction> getOutgoing(final UUID player, int offset, int amount);

    @NotNull List<@NotNull EconomyTransaction> getIncoming(final UUID player, int offset, int amount);
}
