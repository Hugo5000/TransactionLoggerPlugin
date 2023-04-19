package at.hugob.plugin.tradelogger;

import at.hugob.plugin.library.command.CommandManager;
import at.hugob.plugin.library.config.YamlFileConfig;
import at.hugob.plugin.tradelogger.data.EconomyTransaction;
import at.hugob.plugin.tradelogger.database.ITradeLogDatabase;
import at.hugob.plugin.tradelogger.database.MySQLTradeLogDatabase;
import at.hugob.plugin.tradelogger.database.SQLiteTradeLogDatabase;
import at.hugob.plugin.tradelogger.listener.EssentialsEconomyListener;
import at.hugob.plugin.tradelogger.listener.GUIListener;
import at.hugob.plugin.tradelogger.listener.PlayerNameListener;
import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.CommandHelpHandler;
import cloud.commandframework.arguments.standard.IntegerArgument;
import cloud.commandframework.arguments.standard.StringArgument;
import cloud.commandframework.extra.confirmation.CommandConfirmationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.kyori.adventure.text.Component.text;

public class TradeLoggerPlugin extends JavaPlugin {
    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy MMMM dd HH:mm:ss");
    public static final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    private final static String commandName = "transactionlog";
    private YamlFileConfig messages;
    private CommandManager commandManager;
    private ITradeLogDatabase database;
    private PlayerNameManager playerNameManager;
    private TransactionLogManager transactionLogManager;

    @Override
    public void onEnable() {
        messages = new YamlFileConfig(this, "messages.yml");
        reloadConfig();
        try {
            commandManager = new CommandManager(this,
                    component -> messages.getComponent("prefix").append(text(" ")).append(component),
                    "/transactionlog help",
                    new CommandConfirmationManager<>(
                            30L,
                            TimeUnit.SECONDS,
                            context -> context.getCommandContext().getSender().sendMessage(messages.getComponent("commands.confirm.needed")),
                            sender -> sender.sendMessage(messages.getComponent("commands.confirm.nothing"))
                    )
            );
        } catch (InstantiationException e) {
            e.printStackTrace();
            setEnabled(false);
            return;
        }
        playerNameManager = new PlayerNameManager(this);
        transactionLogManager = new TransactionLogManager(this);
        createCommands();
        Bukkit.getPluginManager().registerEvents(new GUIListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerNameListener(this), this);
        if (Bukkit.getPluginManager().getPlugin("Essentials") != null) {
            getLogger().info("Tracking Essentials Economy Transactions");
            Bukkit.getPluginManager().registerEvents(new EssentialsEconomyListener(this), this);
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

        dateTimeFormatter = DateTimeFormatter.ofPattern(messages.getString("date-time-format"));

        database = createDatabase();
    }

    private void createCommands() {
        var transactionLogBuilder = commandManager.manager().commandBuilder("transactionlog", "tl");
        var transactionLogMenuBuilder = commandManager.manager().commandBuilder("transactionlogmenu", "tlm");

        commandManager.command(transactionLogBuilder.literal("help", ArgumentDescription.of("The main help command"))
                .permission("tl.commands.help")
                .argument(StringArgument.<CommandSender>builder("query").greedy().asOptional().withSuggestionsProvider((context, string) ->
                        commandManager.manager().createCommandHelpHandler().queryRootIndex(context.getSender()).getEntries().stream()
                                .map(CommandHelpHandler.VerboseHelpEntry::getSyntaxString).collect(Collectors.toList())
                ).withDefaultDescription(ArgumentDescription.of("The start of the command to query")))
                .handler(commandContext -> {
                    String query = commandContext.getOrDefault("query", "");
                    commandManager.queryCommands(query == null ? "" : query, commandContext.getSender());
                })
        );
        commandManager.command(transactionLogBuilder.literal("reload", ArgumentDescription.of("Reloads this plugin"))
                .permission("tl.admin.reload")
                .handler(commandContext -> {
                    final var sender = commandContext.getSender();
                    sender.sendMessage(this.getMessagesConfig().getComponent("commands.reload.start"));
                    this.reloadConfig();
                    sender.sendMessage(this.getMessagesConfig().getComponent("commands.reload.finish"));
                })
        );
        commandManager.command(transactionLogBuilder.literal("version")
                .permission("tl.admin.version")
                .handler(commandContext ->
                        commandContext.getSender().sendMessage(Component.text(getName() + " version " + getPluginMeta().getVersion()))
                ));
        commandManager.command(transactionLogBuilder
                .permission("tl.command.list")
                .senderType(Player.class)
                .handler(commandContext -> {
                    final Player sender = (Player) commandContext.getSender();
                    getTransactionMessage(sender.getUniqueId(), 10, 0, "/tl list").thenAccept(sender::sendMessage);
                }));
        commandManager.command(transactionLogBuilder.literal("list")
                .argument(IntegerArgument.optional("page", 0))
                .argument(IntegerArgument.optional("entries", 10))
                .permission("tl.command.list")
                .senderType(Player.class)
                .handler(commandContext -> {
                    final Player sender = (Player) commandContext.getSender();
                    final int page = commandContext.get("page");
                    final int entries = commandContext.get("entries");
                    getTransactionMessage(sender.getUniqueId(), entries, page, "/tl list").thenAccept(sender::sendMessage);
                }));
        commandManager.command(transactionLogBuilder.literal("view")
                .argument(StringArgument.<CommandSender>builder("player").quoted().withSuggestionsProvider((commandContext, s) -> playerNameManager.allNames().stream().filter(s1 -> s1.toLowerCase().startsWith(s.toLowerCase())).toList()))
                .argument(IntegerArgument.optional("page", 0))
                .argument(IntegerArgument.optional("entries", 10))
                .permission("tl.command.view")
                .handler(commandContext -> {
                    final CommandSender sender = commandContext.getSender();
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
                .senderType(Player.class)
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
                final Component from = transaction.from() == null ? messages.getComponent("commands.list.unknown") : playerNameManager.getDisplayName(transaction.from());
                final Component to = transaction.to() == null ? messages.getComponent("commands.list.unknown") : playerNameManager.getDisplayName(transaction.to());
                if (player.equals(transaction.to()) && player.equals(transaction.from())) {
                    message.append(messages.getComponent("commands.list.self").replaceText(TextReplacementConfig.builder().match("%([^ ]+)%")
                            .replacement((matchResult, builder) -> switch (matchResult.group(1)) {
                                case "from" -> from;
                                case "to" -> to;
                                case "dateTime" -> builder.content(dateTimeFormatter.format(transaction.dateTime()));
                                case "amount" -> builder.content(decimalFormat.format(transaction.amount()));
                                default -> builder;
                            }).build()));
                } else if (player.equals(transaction.to())) {
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
        });
    }

    private ITradeLogDatabase createDatabase() {
        return switch (getConfig().getString("database.type")) {
            case "mysql" -> new MySQLTradeLogDatabase(this,
                    getConfig().getString("database.username"),
                    getConfig().getString("database.password"),
                    getConfig().getString("database.database"),
                    getConfig().getString("database.ip"),
                    getConfig().getInt("database.port"),
                    getConfig().getString("database.table-prefix")
            );
            default -> new SQLiteTradeLogDatabase(this,
                    getConfig().getString("database.table-prefix")
            );
        };
    }

    public YamlFileConfig getMessagesConfig() {
        return messages;
    }

    public ITradeLogDatabase getDatabase() {
        return database;
    }

    public PlayerNameManager getNameManager() {
        return playerNameManager;
    }

    public TransactionLogManager getTransactionLogManager() {
        return transactionLogManager;
    }
}
