package at.hugob.plugin.transactionlogger;

import at.hugob.plugin.transactionlogger.data.PlayerName;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class PlayerNameManager {
    private final HashMap<UUID, @NotNull String> playerNames = new HashMap<>();
    private final HashMap<UUID, @NotNull Component> displayNames = new HashMap<>();
    private final HashMap<String, UUID> uuids = new HashMap<>();
    private final @NotNull TransactionLoggerPlugin plugin;

    public PlayerNameManager(final @NotNull TransactionLoggerPlugin plugin) {
        this.plugin = plugin;
        for (final PlayerName playerName : plugin.getDatabase().getPlayerNames()) {
            displayNames.put(playerName.uuid(), playerName.displayName());
            playerNames.put(playerName.uuid(), playerName.name());
            uuids.put(playerName.name(), playerName.uuid());
            uuids.putIfAbsent(PlainTextComponentSerializer.plainText().serialize(playerName.displayName()), playerName.uuid());
        }
    }

    public @NotNull Component getDisplayName(final @NotNull UUID player) {
        if (player == null) return null;
        if (Bukkit.getOfflinePlayer(player).isOnline()) return Bukkit.getPlayer(player).displayName();
        return displayNames.getOrDefault(player, Component.text(player.toString()));
    }

    public @NotNull String getName(final @NotNull UUID player) {
        if (player == null) return null;
        if (Bukkit.getOfflinePlayer(player).isOnline()) return Bukkit.getPlayer(player).getName();
        return playerNames.getOrDefault(player, player.toString());
    }

    public @Nullable UUID getUUID(final @NotNull String name) {
        return uuids.get(name);
    }

    public void saveName(final Player player) {
        displayNames.put(player.getUniqueId(), player.displayName());
        playerNames.put(player.getUniqueId(), player.getName());
        uuids.put(player.getName(), player.getUniqueId());
        uuids.putIfAbsent(PlainTextComponentSerializer.plainText().serialize(player.displayName()), player.getUniqueId());
        plugin.getDatabase().save(new PlayerName(player.getUniqueId(), player.getName(), player.displayName()));
    }

    public List<String> allNames() {
        return uuids.keySet().stream().toList();
    }
}
