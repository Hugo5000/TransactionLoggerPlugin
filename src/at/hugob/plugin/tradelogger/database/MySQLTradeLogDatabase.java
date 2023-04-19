package at.hugob.plugin.tradelogger.database;

import at.hugob.plugin.library.database.DatabaseUtils;
import at.hugob.plugin.library.database.MySQLDatabase;
import at.hugob.plugin.tradelogger.TradeLoggerPlugin;
import at.hugob.plugin.tradelogger.data.EconomyTransaction;
import at.hugob.plugin.tradelogger.data.PlayerName;
import com.mysql.cj.exceptions.MysqlErrorNumbers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import static java.time.ZoneOffset.UTC;

public class MySQLTradeLogDatabase extends MySQLDatabase<TradeLoggerPlugin> implements ITradeLogDatabase {
    private static final String CREATE_PLAYERS_TABLE = """
            CREATE TABLE IF NOT EXISTS `%prefix%players` (
              `id` BIGINT NOT NULL AUTO_INCREMENT,
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
              `display_name` VARCHAR(255) NOT NULL,
              PRIMARY KEY (`id`),
              UNIQUE(`uuid_bin`)
            );
            """;
    private static final String SET_PLAYER_NAME = """
            INSERT INTO `%prefix%players` (`uuid_bin`, `name`, `display_name`)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE `display_name` = VALUES(`display_name`),`name` = VALUES(`name`);
            """;
    private static final String SELECT_PLAYERS = """
            SELECT `uuid_bin`, `name`, `display_name` FROM `%prefix%players`;
            """;
    private static final String CREATE_TRANSACTIONS_TABLE = """
            CREATE TABLE IF NOT EXISTS `%prefix%transactions` (
              `timestamp` BIGINT NOT NULL,
              `player_id_from` BIGINT NOT NULL,
              `player_id_to` BIGINT NOT NULL,
              `amount` DECIMAL(36, 6) NOT NULL,
              PRIMARY KEY (`timestamp`,`player_id_from`,`player_id_to`,`amount`),
              FOREIGN KEY(`player_id_from`) REFERENCES `%prefix%players`(`id`),
              FOREIGN KEY(`player_id_to`) REFERENCES `%prefix%players`(`id`)
            );
            """;
    private static final String ADD_TRANSACTION = """
            INSERT INTO `%prefix%transactions` (`timestamp`,`player_id_from`, `player_id_to`, `amount`)
            VALUES (?, ?, ?, ?);
            """;
    private static final String SELECT_TRANSACTIONS = """
            SELECT `timestamp`, `player_from`.`uuid_bin` as `from_uuid`, `player_to`.`uuid_bin` as `to_uuid`, `amount` FROM `%prefix%transactions` 
            LEFT JOIN `%prefix%players` `player_from` ON `player_id_from` = `player_from`.`id`
            LEFT JOIN `%prefix%players` `player_to` ON `player_id_to` = `player_to`.`id`
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
    public MySQLTradeLogDatabase(@NotNull TradeLoggerPlugin plugin, @NotNull String user, @NotNull String password, @NotNull String database, @NotNull String ip, int port, @NotNull String tablePrefix) {
        super(plugin, user, password, database, ip, port, tablePrefix);
        createTables();
    }

    @Override
    protected void createTables() {
        try (var con = getConnection();
             var statement = con.createStatement()) {
            try (var res = con.getMetaData().getTables(null, null, "%prefix%players".replace("%prefix%", tablePrefix),null)) {
                if(!res.next()) {
                    statement.execute(CREATE_PLAYERS_TABLE.replace("%prefix%", tablePrefix));
                    saveNames(new PlayerName(null, "", Component.empty()));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create the name table in the db: ", e);
        }
        try (var con = getConnection();
             var statement = con.createStatement()) {
            statement.execute(CREATE_TRANSACTIONS_TABLE.replace("%prefix%", tablePrefix));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create the transactions table in the db: ", e);
        }
    }

    @Override
    public void saveNames(@NotNull PlayerName playerName) {
        try (var con = getConnection();
             var statement = con.prepareStatement(SET_PLAYER_NAME.replace("%prefix%", tablePrefix))) {
            statement.setBytes(1, DatabaseUtils.convertUuidToBinary(playerName.uuid()));
            statement.setString(2, playerName.name());
            statement.setString(3, GsonComponentSerializer.gson().serialize(playerName.displayName()));
            statement.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                saveNames(playerName);
            } else {
                plugin.getLogger().log(Level.SEVERE, "Could not set player name in the db: ", e);
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
                plugin.getLogger().log(Level.SEVERE, "Could not set player name in the db: ", e);
            }
        }
        return result;
    }

    @Override
    public void save(@NotNull EconomyTransaction transaction) {
        try (var con = getConnection();
             var statement = con.prepareStatement(ADD_TRANSACTION.replace("%prefix%", tablePrefix))) {
            statement.setLong(1, transaction.dateTime().toInstant().toEpochMilli());
            statement.setBytes(2, DatabaseUtils.convertUuidToBinary(transaction.from()));
            statement.setBytes(3, DatabaseUtils.convertUuidToBinary(transaction.to()));
            statement.setBigDecimal(4, transaction.amount());
            statement.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                save(transaction);
            } else {
                plugin.getLogger().log(Level.SEVERE, "Could not set player name in the db: ", e);
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
                while (resultSet.next()) {
                    result.add(new EconomyTransaction(
                            DatabaseUtils.convertBytesToUUID(resultSet.getBytes("from_uuid")),
                            DatabaseUtils.convertBytesToUUID(resultSet.getBytes("to_uuid")),
                            resultSet.getBigDecimal("amount"),
                            Instant.ofEpochMilli(resultSet.getLong("timestamp")).atZone(UTC)
                    ));
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                return get(player, offset, amount);
            } else {
                plugin.getLogger().log(Level.SEVERE, String.format("Could not get Transactions concerning %s, offset %s, amount %s",player, offset, amount), e);
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
                while (resultSet.next()) {
                    result.add(new EconomyTransaction(
                            DatabaseUtils.convertBytesToUUID(resultSet.getBytes("from_uuid")),
                            DatabaseUtils.convertBytesToUUID(resultSet.getBytes("to_uuid")),
                            resultSet.getBigDecimal("amount"),
                            Instant.ofEpochMilli(resultSet.getLong("timestamp")).atZone(UTC)
                    ));
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                return get(player, offset, amount);
            } else {
                plugin.getLogger().log(Level.SEVERE, String.format("Could not get Transactions from %s, offset %s, amount %s",player, offset, amount), e);
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
                while (resultSet.next()) {
                    result.add(new EconomyTransaction(
                            DatabaseUtils.convertBytesToUUID(resultSet.getBytes("from_uuid")),
                            DatabaseUtils.convertBytesToUUID(resultSet.getBytes("to_uuid")),
                            resultSet.getBigDecimal("amount"),
                            Instant.ofEpochMilli(resultSet.getLong("timestamp")).atZone(ZoneOffset.systemDefault())
                    ));
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                return get(player, offset, amount);
            } else {
                plugin.getLogger().log(Level.SEVERE, String.format("Could not get Transactions to %s, offset %s, amount %s",player, offset, amount), e);
            }
        }
        return result;
    }
}
