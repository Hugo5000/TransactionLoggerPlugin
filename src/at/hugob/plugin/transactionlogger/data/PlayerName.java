package at.hugob.plugin.transactionlogger.data;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record PlayerName(@NotNull UUID uuid, @NotNull String name, @NotNull Component displayName) {

}
