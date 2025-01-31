package at.hugob.plugin.transactionlogger;

import at.hugob.plugin.library.command.CommandManager;
import at.hugob.plugin.library.config.YamlFileConfig;
import at.hugob.plugin.transactionlogger.data.ConsoleTransactionContext;
import at.hugob.plugin.transactionlogger.data.EconomyTransaction;
import at.hugob.plugin.transactionlogger.database.ITransactionLogDatabase;
import at.hugob.plugin.transactionlogger.database.MySQLTransactionLogDatabase;
import at.hugob.plugin.transactionlogger.database.SQLiteTransactionLogDatabase;
import at.hugob.plugin.transactionlogger.listener.*;
import at.hugob.plugin.transactionlogger.listener.command.EssentialsMenuEcoCommandListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.component.DefaultValue;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.help.result.CommandEntry;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static net.kyori.adventure.text.Component.text;

public class TransactionLoggerPlugin extends JavaPlugin {
    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy MMMM dd HH:mm:ss");
    public static final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    private YamlFileConfig messages;
    private CommandManager commandManager;
    private GUIManager guiManager;
    private ITransactionLogDatabase database;
    private PlayerNameManager playerNameManager;
    private TransactionLogManager transactionLogManager;
    private ConsoleTransactionContext defaultContext;

    @Override
    public void onEnable() {
        messages = new YamlFileConfig(this, "messages.yml");

        guiManager = new GUIManager(this);
        transactionLogManager = new TransactionLogManager(this);
        reloadConfig();
        commandManager = new CommandManager(this,
            component -> messages.getComponent("prefix").append(text(" ")).append(component),
            "/transactionlog help",
            source -> source.source().sendMessage(messages.getComponent("commands.confirm.needed")),
            (source, sourceConfirmationContext) -> source.source().sendMessage(messages.getComponent("commands.confirm.nothing"))
        );
        playerNameManager = new PlayerNameManager(this);
        createCommands();
        Bukkit.getPluginManager().registerEvents(new GUIListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerNameListener(this), this);
        if (Bukkit.getPluginManager().getPlugin("Essentials") != null) {
            getLogger().info("Tracking Essentials Economy Transactions");
            Bukkit.getPluginManager().registerEvents(new EssentialsListener(this), this);
            Bukkit.getPluginManager().registerEvents(new EssentialsMenuEcoCommandListener(this), this);
        }
        if (Bukkit.getPluginManager().getPlugin("ChestShop") != null) {
            getLogger().info("Tracking ChestShop Transactions");
            Bukkit.getPluginManager().registerEvents(new ChestShopListener(this), this);
        }
        if (Bukkit.getPluginManager().getPlugin("BeastWithdraw") != null) {
            getLogger().info("Tracking BeastWithdraw Transactions");
            Bukkit.getPluginManager().registerEvents(new BeastWithdrawListener(this), this);
        }
        if (Bukkit.getPluginManager().getPlugin("MoneyFromMobs") != null) {
            getLogger().info("Tracking MoneyFromMobs Transactions");
            Bukkit.getPluginManager().registerEvents(new MoneyFromMobsListener(this), this);
        }
        if (Bukkit.getPluginManager().getPlugin("ShopGUIPlus") != null) {
            getLogger().info("Tracking ShopGUIPlus Transactions");
            Bukkit.getPluginManager().registerEvents(new ShopGUIListener(this), this);
        }
    }

    @Override
    public void onDisable() {
        transactionLogManager.disable();
    }

    @Override
    public void reloadConfig() {
        saveDefaultConfig();
        super.reloadConfig();
        messages.reload();

        guiManager.reload();

        dateTimeFormatter = DateTimeFormatter.ofPattern(messages.getString("date-time-format"));

        database = createDatabase();

        transactionLogManager.reload();

        defaultContext = new ConsoleTransactionContext("Console", messages.getComponent("console.Unknown"));
    }

    private void createCommands() {
        var transactionLogBuilder = commandManager.manager().commandBuilder("transactionlog", "tl");
        var transactionLogMenuBuilder = commandManager.manager().commandBuilder("transactionlogmenu", "tlm");

        commandManager.command(transactionLogBuilder.literal("help", Description.of("The main help command"))
            .permission("tl.command.help")
            .optional("query", StringParser.greedyStringParser(),
                DefaultValue.constant(""),
                Description.of("The start of the command to query"),
                SuggestionProvider.blocking((ctx, in) -> commandManager.manager().createHelpHandler()
                    .queryRootIndex(ctx.sender())
                    .entries()
                    .stream()
                    .map(CommandEntry::syntax)
                    .map(Suggestion::suggestion)
                    .collect(Collectors.toList())
                )
            ).handler(commandContext -> {
                commandManager.queryCommands(commandContext.get("query"), commandContext.sender());
            })
        );
        commandManager.command(transactionLogBuilder.literal("reload", Description.of("Reloads this plugin"))
            .permission("tl.admin.reload")
            .handler(commandContext -> {
                final var sender = commandContext.sender();
                sender.source().sendMessage(this.getMessagesConfig().getComponent("commands.reload.start"));
                this.reloadConfig();
                sender.source().sendMessage(this.getMessagesConfig().getComponent("commands.reload.finish"));
            })
        );
        commandManager.command(transactionLogBuilder.literal("version", Description.of("Prints the current Plugin version"))
            .permission("tl.admin.version")
            .handler(commandContext ->
                commandContext.sender().source().sendMessage(Component.text(getName() + " version " + getPluginMeta().getVersion()))
            ));
        commandManager.command(transactionLogBuilder
            .permission("tl.command.list")
            .senderType(PlayerSource.class)
            .handler(commandContext -> {
                final Player sender = commandContext.sender().source();
                getTransactionMessage(sender.getUniqueId(), 10, 0, "/tl list").thenAccept(sender::sendMessage);
            }));
        commandManager.command(transactionLogBuilder.literal("list")
            .optional("page", IntegerParser.integerParser(), DefaultValue.constant(0),
                Description.of("Page Number"))
            .optional("entries", IntegerParser.integerParser(), DefaultValue.constant(10),
                Description.of("Number of Entries on each Page"))
            .permission("tl.command.list")
            .senderType(PlayerSource.class)
            .handler(commandContext -> {
                final Player sender = commandContext.sender().source();
                final int page = commandContext.get("page");
                final int entries = commandContext.get("entries");
                getTransactionMessage(sender.getUniqueId(), entries, page, "/tl list").thenAccept(sender::sendMessage);
            }));
        commandManager.command(transactionLogBuilder.literal("view")
            .required("player", StringParser.quotedStringParser(), Description.of("Player to View Transactions of"),
                SuggestionProvider.blocking((context, input) -> playerNameManager.allNames().stream().map(Suggestion::suggestion).toList()))
            .optional("page", IntegerParser.integerParser(), DefaultValue.constant(0),
                Description.of("Page Number"))
            .optional("entries", IntegerParser.integerParser(), DefaultValue.constant(10),
                Description.of("Number of Entries on each Page"))
            .permission("tl.command.view")
            .handler(commandContext -> {
                final CommandSender sender = commandContext.sender().source();
                final String playerName = commandContext.get("player");
                final UUID uuid = playerNameManager.getUUID(playerName);
                if (uuid == null) {
                    sender.sendMessage(messages.getComponent("commands.view.unknown-target"));
                    return;
                }
                final int page = commandContext.get("page");
                final int entries = commandContext.get("entries");
                getTransactionMessage(uuid, entries, page, String.format("/tl view %s ", playerName)).thenAccept(sender::sendMessage);
            }));
        commandManager.command(transactionLogMenuBuilder
            .permission("tl.command.menu")
            .senderType(PlayerSource.class)
            .handler(commandContext -> {
                final Player sender = commandContext.sender().source();
                guiManager.openTransactionGUI(sender, sender.getUniqueId());
            })
        );
    }

    public CompletableFuture<Component> getTransactionMessage(UUID player, int amount, int page, String command) {
        return transactionLogManager.get(player, amount * page, amount).<Component>thenApply(transactions -> {
            var message = Component.text().append(messages.getComponent("commands.list.header").replaceText(TextReplacementConfig.builder().match("%([^ ]+)%")
                .replacement((matchResult, builder) -> switch (matchResult.group(1)) {
                    case "playerName" -> playerNameManager.getDisplayName(player);
                    default -> builder;
                }).build()));
            for (EconomyTransaction transaction : transactions) {
                message.append(Component.newline());
                final Component from = transaction.from() == null ? transaction.consoleContextOr(defaultContext).displayName() : playerNameManager.getDisplayName(transaction.from());
                final Component to = transaction.to() == null ? transaction.consoleContextOr(defaultContext).displayName() : playerNameManager.getDisplayName(transaction.to());
                if (player.equals(transaction.to())) {
                    message.append(messages.getComponent("commands.list.gained").replaceText(TextReplacementConfig.builder().match("%([^ ]+)%")
                        .replacement((matchResult, builder) -> switch (matchResult.group(1)) {
                            case "from" -> from;
                            case "to" -> to;
                            case "dateTime" -> builder.content(dateTimeFormatter.format(transaction.dateTime()));
                            case "amount" -> builder.content(decimalFormat.format(transaction.amount()));
                            default -> builder;
                        }).build()));
                } else if (player.equals(transaction.from())) {
                    message.append(messages.getComponent("commands.list.paid").replaceText(TextReplacementConfig.builder().match("%([^ ]+)%")
                        .replacement((matchResult, builder) -> switch (matchResult.group(1)) {
                            case "from" -> from;
                            case "to" -> to;
                            case "dateTime" -> builder.content(dateTimeFormatter.format(transaction.dateTime()));
                            case "amount" -> builder.content(decimalFormat.format(transaction.amount()));
                            default -> builder;
                        }).build()));
                }
            }
            message.append(Component.newline());
            if (page == 0) {
                message.append(messages.getComponent("commands.list.first-footer").replaceText(TextReplacementConfig.builder().match("%([^ ]+)%")
                    .replacement((matchResult, builder) -> switch (matchResult.group(1)) {
                        case "page" -> builder.content(String.valueOf(page + 1));
                        case "next" -> {
                            var text = messages.getComponent("commands.list.next.text").replaceText(TextReplacementConfig.builder().match("%([^ ]+)%")
                                .replacement((matchResult2, builder2) -> switch (matchResult2.group(1)) {
                                    case "page" -> builder2.content(String.valueOf(page + 2));
                                    default -> builder2;
                                }).build()).clickEvent(ClickEvent.runCommand(String.format(command + " %s %s", page + 1, amount)));
                            if (!messages.getString("commands.list.next.hover").isBlank())
                                text = text.hoverEvent(HoverEvent.showText(messages.getComponent("commands.list.next.hover")));
                            yield builder.content("").append(text);
                        }
                        default -> builder;
                    }).build()));
            } else {
                message.append(messages.getComponent("commands.list.footer").replaceText(TextReplacementConfig.builder().match("%([^ ]+)%")
                    .replacement((matchResult, builder) -> switch (matchResult.group(1)) {
                        case "previous" -> {
                            var text = messages.getComponent("commands.list.previous.text").replaceText(TextReplacementConfig.builder().match("%([^ ]+)%")
                                .replacement((matchResult2, builder2) -> switch (matchResult2.group(1)) {
                                    case "page" -> builder2.content(String.valueOf(page));
                                    default -> builder2;
                                }).build()).clickEvent(ClickEvent.runCommand(String.format(command + " %s %s", page - 1, amount)));
                            if (!messages.getString("commands.list.previous.hover").isBlank())
                                text = text.hoverEvent(HoverEvent.showText(messages.getComponent("commands.list.previous.hover")));
                            yield builder.content("").append(text);
                        }
                        case "page" -> builder.content(String.valueOf(page + 1));
                        case "next" -> {
                            var text = messages.getComponent("commands.list.next.text").replaceText(TextReplacementConfig.builder().match("%([^ ]+)%")
                                .replacement((matchResult2, builder2) -> switch (matchResult2.group(1)) {
                                    case "page" -> builder2.content(String.valueOf(page + 2));
                                    default -> builder2;
                                }).build()).clickEvent(ClickEvent.runCommand(String.format(command + " %s %s", page + 1, amount)));
                            if (!messages.getString("commands.list.next.hover").isBlank())
                                text = text.hoverEvent(HoverEvent.showText(messages.getComponent("commands.list.next.hover")));
                            yield builder.content("").append(text);
                        }
                        default -> builder;
                    }).build()));
            }
            return message.build();
        }).exceptionally(t -> {
            getLogger().log(Level.SEVERE, t.getMessage(), t);
            return Component.text(t.getMessage());
        });
    }

    private ITransactionLogDatabase createDatabase() {
        return switch (getConfig().getString("database.type")) {
            case "mysql" -> new MySQLTransactionLogDatabase(this,
                getConfig().getString("database.username"),
                getConfig().getString("database.password"),
                getConfig().getString("database.database"),
                getConfig().getString("database.ip"),
                getConfig().getInt("database.port"),
                getConfig().getString("database.table-prefix")
            );
            default -> new SQLiteTransactionLogDatabase(this,
                getConfig().getString("database.table-prefix")
            );
        };
    }

    public YamlFileConfig getMessagesConfig() {
        return messages;
    }

    public ITransactionLogDatabase getDatabase() {
        return database;
    }

    public PlayerNameManager getNameManager() {
        return playerNameManager;
    }

    public TransactionLogManager getTransactionLogManager() {
        return transactionLogManager;
    }

    public DateTimeFormatter getDateTimeFormatter() {
        return dateTimeFormatter;
    }

    public ConsoleTransactionContext getDefaultContext() {
        return defaultContext;
    }
}
