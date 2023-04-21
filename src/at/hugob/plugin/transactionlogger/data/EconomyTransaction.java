package at.hugob.plugin.transactionlogger.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

public class EconomyTransaction {
    private final @NotNull ZonedDateTime dateTime;
    private @Nullable UUID from;
    private @Nullable UUID to;
    private BigDecimal amount;
    private @Nullable ConsoleTransactionContext consoleContext;

    public EconomyTransaction(@NotNull ZonedDateTime dateTime, @Nullable UUID from, @Nullable UUID to, @NotNull BigDecimal amount, @Nullable ConsoleTransactionContext consoleContext) {
        this.dateTime = dateTime;
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.consoleContext = consoleContext;
    }

    public UUID from() {
        return from;
    }

    public void from(UUID from) {
        if (this.from == null) this.from = from;
    }

    public UUID to() {
        return to;
    }

    public void to(UUID to) {
        if (this.to == null) this.to = to;
    }

    public BigDecimal amount() {
        return amount;
    }

    public @NotNull ZonedDateTime dateTime() {
        return dateTime;
    }

    public @Nullable ConsoleTransactionContext consoleContext() {
        return consoleContext;
    }

    public @Nullable ConsoleTransactionContext consoleContextOr(ConsoleTransactionContext consoleContext) {
        return this.consoleContext != null ? this.consoleContext : consoleContext;
    }

    public void consoleContext(ConsoleTransactionContext consoleContext) {
        if (this.consoleContext == null) this.consoleContext = consoleContext;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EconomyTransaction{");
        sb.append("dateTime=").append(dateTime);
        sb.append(", from=").append(from);
        sb.append(", to=").append(to);
        sb.append(", amount=").append(amount);
        sb.append(", consoleContext=").append(consoleContext);
        sb.append('}');
        return sb.toString();
    }
}
