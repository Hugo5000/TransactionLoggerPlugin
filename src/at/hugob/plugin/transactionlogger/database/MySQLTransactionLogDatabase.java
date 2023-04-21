package at.hugob.plugin.transactionlogger.database;

import at.hugob.plugin.library.database.DatabaseUtils;
import at.hugob.plugin.library.database.MySQLDatabase;
import at.hugob.plugin.transactionlogger.TransactionLoggerPlugin;
import at.hugob.plugin.transactionlogger.data.ConsoleTransactionContext;
import at.hugob.plugin.transactionlogger.data.EconomyTransaction;
import at.hugob.plugin.transactionlogger.data.PlayerName;
import at.hugob.plugin.transactionlogger.data.PluginTransactionContext;
import com.mysql.cj.exceptions.MysqlErrorNumbers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class MySQLTransactionLogDatabase extends MySQLDatabase<TransactionLoggerPlugin> implements ITransactionLogDatabase {
    private static final String CREATE_PLAYERS_TABLE = """
            CREATE TABLE IF NOT EXISTS `%prefix%players` (
              `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
              `uuid_bin` BINARY(16) NOT NULL,
              `uuid_text` CHAR(36) generated always AS (
                  INSERT(
                      INSERT(
                          INSERT(
                              INSERT(
                                  hex(`uuid_bin`), 9, 0, '-'
                              ) , 14, 0, '-'
                          ) , 19, 0, '-'
                      ) , 24, 0, '-'
                  )
              ) virtual,
              `name` VARCHAR(16) NOT NULL,
              `display_name` VARCHAR(512) NOT NULL,
              PRIMARY KEY (`id`),
              UNIQUE(`uuid_bin`)
            );
            """;
    private static final String ADD_PLAYER_NAME = """
            INSERT IGNORE INTO `%prefix%players` (`uuid_bin`, `name`, `display_name`)
            VALUES (?, ?, ?);
            """;
    private static final String UPDATE_PLAYER_NAME = """
            UPDATE `%prefix%players`
            SET `name` = ?, `display_name` = ?
            WHERE `uuid_bin` = ?;
            """;
    private static final String SELECT_PLAYERS = """
            SELECT `uuid_bin`, `name`, `display_name` FROM `%prefix%players`;
            """;
    private static final String CREATE_CONSOLE_CONTEXT_TABLE = """
            CREATE TABLE IF NOT EXISTS `%prefix%console_context` (
              `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
              `name` VARCHAR(255) NOT NULL,
              `display_name` VARCHAR(512) NOT NULL,
              PRIMARY KEY (`id`),
              UNIQUE(`name`)
            );
            """;
    private static final String ADD_CONSOLE_CONTEXT = """
            INSERT INTO `%prefix%console_context` (`name`, `display_name`)
            VALUES (?, ?);
            """;
    private static final String UPDATE_CONSOLE_CONTEXT = """
            UPDATE `%prefix%console_context`
            SET `display_name` = ?
            WHERE `name = ?;
            """;
    private static final String CREATE_TRANSACTIONS_TABLE = """
            CREATE TABLE IF NOT EXISTS `%prefix%transactions` (
              `timestamp` BIGINT NOT NULL,
              `player_id_from` BIGINT NULL,
              `player_id_to` BIGINT NULL,
              `amount` DECIMAL(36, 6) NOT NULL,
              `console_context` BIGINT,
              PRIMARY KEY (`timestamp`,`amount`),
              FOREIGN KEY(`player_id_from`) REFERENCES `%prefix%players`(`id`),
              FOREIGN KEY(`player_id_to`) REFERENCES `%prefix%players`(`id`),
              FOREIGN KEY(`console_context`) REFERENCES `%prefix%console_context`(`id`)
            );
            """;
    private static final String ADD_TRANSACTION = """
            INSERT INTO `%prefix%transactions` (`timestamp`,`player_id_from`, `player_id_to`, `amount`, `console_context`)
            VALUES (?, (SELECT id  FROM `%prefix%players` WHERE uuid_bin = ?), (SELECT id  FROM `%prefix%players` WHERE uuid_bin = ?), ?, (SELECT id  FROM `%prefix%console_context` WHERE name = ?));
            """;
    private static final String SELECT_TRANSACTIONS = """
            SELECT `timestamp`, `player_from`.`uuid_bin` as `from_uuid`, `player_to`.`uuid_bin` as `to_uuid`, `amount`, `context`.`name` as `console_name`, `context`.`display_name` as `console_display_name` FROM `%prefix%transactions`
            LEFT JOIN `%prefix%players` `player_from` ON `player_id_from` = `player_from`.`id`
            LEFT JOIN `%prefix%players` `player_to` ON `player_id_to` = `player_to`.`id`
            LEFT JOIN `%prefix%console_context` `context` ON `console_context` = `context`.`id`
            WHERE `player_from`.`uuid_bin` = ?
               OR `player_to`.`uuid_bin`   = ?
            ORDER BY `timestamp` DESC LIMIT ? OFFSET ?;
            """;
    private static final String SELECT_TRANSACTIONS_FROM = """
            SELECT `timestamp`, `from_name`.`uuid_bin` as `from_uuid`, `to_name`.`uuid_bin` as `to_uuid`, `amount` FROM `%prefix%transactions`
            LEFT JOIN `%prefix%players` `from_name` ON `player_id_from` = `from_name`.`id`
            LEFT JOIN `%prefix%players` `to_name` ON `player_id_to` = `to_name`.`id`
            WHERE `from_name`.`uuid_bin` = ?
            ORDER BY `timestamp` DESC LIMIT ? OFFSET ?;
            """;
    private static final String SELECT_TRANSACTIONS_TO = """
            SELECT `timestamp`, `from_name`.`uuid_bin` as `from_uuid`, `to_name`.`uuid_bin` as `to_uuid`, `amount` FROM `%prefix%transactions`
            LEFT JOIN `%prefix%players` `from_name` ON `player_id_from` = `from_name`.`id`
            LEFT JOIN `%prefix%players` `to_name` ON `player_id_to` = `to_name`.`id`
            WHERE `to_name`.`uuid_bin` = ?
            ORDER BY `timestamp` DESC LIMIT ? OFFSET ?;
            """;

    /**
     * Instantiates a new MySQL Database connection
     *
     * @param plugin      the plugin that instantiates this database connection
     * @param user        the username from the database login
     * @param password    the password from the database login
     * @param database    the database name
     * @param ip          the ip that points to the database
     * @param port        the port that points to the database
     * @param tablePrefix the prefix for tables that are created in the database
     */
    public MySQLTransactionLogDatabase(@NotNull TransactionLoggerPlugin plugin, @NotNull String user, @NotNull String password, @NotNull String database, @NotNull String ip, int port, @NotNull String tablePrefix) {
        super(plugin, user, password, database, ip, port, tablePrefix);
        createTables();
    }

    @Override
    protected void createTables() {
        try (var con = getConnection();
             var statement = con.createStatement()) {
            statement.execute(CREATE_PLAYERS_TABLE.replace("%prefix%", tablePrefix));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create the name table in the db: ", e);
        }
        try (var con = getConnection();
             var statement = con.createStatement()) {
            statement.execute(CREATE_CONSOLE_CONTEXT_TABLE.replace("%prefix%", tablePrefix));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create the console context table in the db: ", e);
        }
        try (var con = getConnection();
             var statement = con.createStatement()) {
            statement.execute(CREATE_TRANSACTIONS_TABLE.replace("%prefix%", tablePrefix));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create the transactions table in the db: ", e);
        }
    }

    @Override
    public void save(@NotNull PlayerName playerName) {
        try (var con = getConnection();
             var statement = con.prepareStatement(UPDATE_PLAYER_NAME.replace("%prefix%", tablePrefix))) {
            statement.setString(1, playerName.name());
            statement.setString(2, GsonComponentSerializer.gson().serialize(playerName.displayName()));
            statement.setBytes(3, DatabaseUtils.convertUuidToBinary(playerName.uuid()));
            var updated = statement.executeUpdate();
            if (updated == 0) {
                try (var statement2 = con.prepareStatement(ADD_PLAYER_NAME.replace("%prefix%", tablePrefix))) {
                    statement2.setBytes(1, DatabaseUtils.convertUuidToBinary(playerName.uuid()));
                    statement2.setString(2, playerName.name());
                    statement2.setString(3, GsonComponentSerializer.gson().serialize(playerName.displayName()));
                    statement2.execute();
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                save(playerName);
            } else {
                plugin.getLogger().log(Level.SEVERE, "Could not set player name in the db: ", e);
            }
        }
    }

    @Override
    public void saveNew(@NotNull PlayerName playerName) {
        try (var con = getConnection();
             var statement = con.prepareStatement(ADD_PLAYER_NAME.replace("%prefix%", tablePrefix))) {
            statement.setBytes(1, DatabaseUtils.convertUuidToBinary(playerName.uuid()));
            statement.setString(2, playerName.name());
            statement.setString(3, GsonComponentSerializer.gson().serialize(playerName.displayName()));
            statement.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                save(playerName);
            } else {
                plugin.getLogger().log(Level.SEVERE, "Could not save new player name in the db: ", e);
            }
        }
    }

    @Override
    public @NotNull List<PlayerName> getPlayerNames() {
        LinkedList<PlayerName> result = new LinkedList<>();
        try (var con = getConnection();
             var statement = con.prepareStatement(SELECT_PLAYERS.replace("%prefix%", tablePrefix));
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.add(new PlayerName(
                        DatabaseUtils.convertBytesToUUID(resultSet.getBytes("uuid_bin")),
                        resultSet.getString("name"),
                        GsonComponentSerializer.gson().deserialize(resultSet.getString("display_name"))
                ));
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                return getPlayerNames();
            } else {
                plugin.getLogger().log(Level.SEVERE, "Could not get player name in the db: ", e);
            }
        }
        return result;
    }

    @Override
    public void save(@NotNull EconomyTransaction transaction) {
        if (transaction.from() != null)
            saveNew(new PlayerName(transaction.from(), transaction.from().toString(), Component.text(transaction.from().toString())));
        if (transaction.to() != null)
            saveNew(new PlayerName(transaction.to(), transaction.to().toString(), Component.text(transaction.to().toString())));
        try (var con = getConnection();
             var statement = con.prepareStatement(ADD_TRANSACTION.replace("%prefix%", tablePrefix))) {
            statement.setLong(1, transaction.dateTime().toInstant().toEpochMilli());
            statement.setBytes(2, DatabaseUtils.convertUuidToBinary(transaction.from()));
            statement.setBytes(3, DatabaseUtils.convertUuidToBinary(transaction.to()));
            statement.setBigDecimal(4, transaction.amount());
            statement.setString(5, transaction.consoleContext() == null ? null : transaction.consoleContext().name());
            statement.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                save(transaction);
            } else if (e.getErrorCode() == MysqlErrorNumbers.ER_DUP_ENTRY) {
                save(new EconomyTransaction(transaction.dateTime().plus(1, ChronoUnit.MILLIS), transaction.from(), transaction.to(), transaction.amount(), transaction.consoleContext()));
            } else {
                plugin.getLogger().log(Level.SEVERE, String.format("Could not add transaction in the db %s: ", e.getErrorCode()), e);
            }
        }
    }

    @Override
    public void save(final @NotNull ConsoleTransactionContext consoleTransactionContext) {
        try (var con = getConnection();
             var statement = con.prepareStatement(UPDATE_CONSOLE_CONTEXT.replace("%prefix%", tablePrefix))) {
            statement.setString(1, GsonComponentSerializer.gson().serialize(consoleTransactionContext.displayName()));
            statement.setString(2, consoleTransactionContext.name());
            var updated = statement.executeUpdate();
            if (updated == 0) {
                try (var statement2 = con.prepareStatement(ADD_CONSOLE_CONTEXT.replace("%prefix%", tablePrefix))) {
                    statement2.setString(1, consoleTransactionContext.name());
                    statement2.setString(2, GsonComponentSerializer.gson().serialize(consoleTransactionContext.displayName()));
                    statement2.execute();
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                save(consoleTransactionContext);
            } else {
                plugin.getLogger().log(Level.SEVERE, "Could not set console context in the db: ", e);
            }
        }
    }

    @Override
    public @NotNull List<@NotNull EconomyTransaction> get(UUID player, int offset, int amount) {
        ArrayList<EconomyTransaction> result = new ArrayList<>(amount);
        try (var con = getConnection();
             var statement = con.prepareStatement(SELECT_TRANSACTIONS.replace("%prefix%", tablePrefix))) {
            statement.setBytes(1, DatabaseUtils.convertUuidToBinary(player));
            statement.setBytes(2, DatabaseUtils.convertUuidToBinary(player));
            statement.setInt(3, amount);
            statement.setInt(4, offset);
            try (var resultSet = statement.executeQuery()) {
                getEconomyTransactions(result, resultSet);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                return get(player, offset, amount);
            } else {
                plugin.getLogger().log(Level.SEVERE, String.format("Could not get Transactions concerning %s, offset %s, amount %s", player, offset, amount), e);
            }
        }
        return result;
    }

    @Override
    public @NotNull List<@NotNull EconomyTransaction> getOutgoing(UUID player, int offset, int amount) {
        ArrayList<EconomyTransaction> result = new ArrayList<>(amount);
        try (var con = getConnection();
             var statement = con.prepareStatement(SELECT_TRANSACTIONS_FROM.replace("%prefix%", tablePrefix))) {
            statement.setBytes(1, DatabaseUtils.convertUuidToBinary(player));
            statement.setInt(2, amount);
            statement.setInt(3, offset);
            try (var resultSet = statement.executeQuery()) {
                getEconomyTransactions(result, resultSet);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                return get(player, offset, amount);
            } else {
                plugin.getLogger().log(Level.SEVERE, String.format("Could not get Transactions from %s, offset %s, amount %s", player, offset, amount), e);
            }
        }
        return result;
    }

    @Override
    public @NotNull List<@NotNull EconomyTransaction> getIncoming(UUID player, int offset, int amount) {
        ArrayList<EconomyTransaction> result = new ArrayList<>(amount);
        try (var con = getConnection();
             var statement = con.prepareStatement(SELECT_TRANSACTIONS_TO.replace("%prefix%", tablePrefix))) {
            statement.setBytes(1, DatabaseUtils.convertUuidToBinary(player));
            statement.setInt(2, amount);
            statement.setInt(3, offset);
            try (var resultSet = statement.executeQuery()) {
                getEconomyTransactions(result, resultSet);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                return get(player, offset, amount);
            } else {
                plugin.getLogger().log(Level.SEVERE, String.format("Could not get Transactions to %s, offset %s, amount %s", player, offset, amount), e);
            }
        }
        return result;
    }

    private static void getEconomyTransactions(ArrayList<EconomyTransaction> result, ResultSet resultSet) throws SQLException {
        while (resultSet.next()) {
            final ConsoleTransactionContext consoleTransactionContext;
            final String consoleName = resultSet.getString("console_name");
            if (consoleName == null) {
                consoleTransactionContext = null;
            } else {
                final @Nullable Plugin p = Bukkit.getPluginManager().getPlugin(consoleName);
                if (p == null) {
                    consoleTransactionContext = new ConsoleTransactionContext(consoleName, GsonComponentSerializer.gson().deserialize(resultSet.getString("console_display_name")));
                } else {
                    consoleTransactionContext = new PluginTransactionContext<>(p, GsonComponentSerializer.gson().deserialize(resultSet.getString("console_display_name")));
                }
            }
            result.add(new EconomyTransaction(
                    Instant.ofEpochMilli(resultSet.getLong("timestamp")).atZone(ZoneOffset.systemDefault()),
                    DatabaseUtils.convertBytesToUUID(resultSet.getBytes("from_uuid")),
                    DatabaseUtils.convertBytesToUUID(resultSet.getBytes("to_uuid")),
                    resultSet.getBigDecimal("amount"),
                    consoleTransactionContext
            ));
        }
    }
}
