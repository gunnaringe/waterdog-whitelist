package xyz.haxxor.waterdogwhitelist;

import dev.waterdog.waterdogpe.command.Command;
import dev.waterdog.waterdogpe.command.CommandSender;
import dev.waterdog.waterdogpe.command.CommandSettings;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;

/**
 * Console-only by design: permission is left unset in the proxy's permissions/permissions_default,
 * and ConsoleCommandSender.hasPermission() always returns true, so only whoever can attach to the
 * pod's console can manage the whitelist. Admin scripts go through CliWatcher instead of this
 * command directly, since kubectl can't script the proxy's interactive console reliably.
 */
public class WhitelistCommand extends Command {

    private final WhitelistOperations operations;
    private final Logger logger;

    public WhitelistCommand(WhitelistOperations operations, Logger logger) {
        super("whitelist", CommandSettings.builder()
                .setDescription("Manage the join whitelist")
                .setPermission("waterdogwhitelist.admin")
                .setUsageMessage("whitelist <add|remove|list|attempts> [xuid] [gamertag]")
                .build());
        this.operations = operations;
        this.logger = logger;
    }

    @Override
    public boolean onExecute(CommandSender sender, String alias, String[] args) {
        if (args.length == 0) {
            return false;
        }

        try {
            switch (args[0].toLowerCase()) {
                case "add" -> {
                    if (args.length < 2) {
                        sender.sendMessage("Usage: whitelist add <xuid> [gamertag]");
                        return true;
                    }
                    sender.sendMessage(this.operations.add(args[1], args.length > 2 ? args[2] : null));
                }
                case "remove" -> {
                    if (args.length < 2) {
                        sender.sendMessage("Usage: whitelist remove <xuid>");
                        return true;
                    }
                    sender.sendMessage(this.operations.remove(args[1]));
                }
                case "list" -> sender.sendMessage(this.operations.list());
                case "attempts" -> {
                    int limit = 10;
                    if (args.length > 1) {
                        try {
                            limit = Integer.parseInt(args[1]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage("Usage: whitelist attempts [count]");
                            return true;
                        }
                    }
                    sender.sendMessage(this.operations.attempts(limit));
                }
                default -> {
                    return false;
                }
            }
        } catch (SQLException e) {
            sender.sendMessage("Whitelist database error, see console log");
            this.logger.error("Whitelist command '{}' failed", args[0], e);
        }
        return true;
    }
}
