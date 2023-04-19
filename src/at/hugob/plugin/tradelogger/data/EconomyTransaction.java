package at.hugob.plugin.tradelogger.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

public record EconomyTransaction(@Nullable UUID from, @Nullable UUID to, BigDecimal amount,
                                 @NotNull ZonedDateTime dateTime) {
}
