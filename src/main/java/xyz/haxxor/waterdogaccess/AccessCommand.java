package xyz.haxxor.waterdogaccess;

import dev.waterdog.waterdogpe.command.Command;
import dev.waterdog.waterdogpe.command.CommandSender;
import dev.waterdog.waterdogpe.command.CommandSettings;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;

/**
 * Console-only by design: permission is left unset in the proxy's permissions/permissions_default,
 * and ConsoleCommandSender.hasPermission() always returns true, so only whoever can attach to the
 * pod's console can manage access. Admin scripts go through the HTTP API (ApiServer) instead of
 * this command directly - kubectl can't script the proxy's interactive console reliably.
 */
public class AccessCommand extends Command {

    private final AccessOperations operations;
    private final Logger logger;

    public AccessCommand(AccessOperations operations, Logger logger) {
        super("access", CommandSettings.builder()
                .setDescription("Manage per-world join access")
                .setPermission("waterdogaccess.admin")
                .setUsageMessage("access <grant|revoke|list|attempts> [xuid] [world] [gamertag]")
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
                case "grant" -> {
                    if (args.length < 2) {
                        sender.sendMessage("Usage: access grant <xuid> [world] [gamertag]");
                        return true;
                    }
                    String world = args.length > 2 ? args[2] : AccessDatabase.WILDCARD;
                    sender.sendMessage(this.operations.grant(args[1], world, args.length > 3 ? args[3] : null));
                }
                case "revoke" -> {
                    if (args.length < 2) {
                        sender.sendMessage("Usage: access revoke <xuid> [world]");
                        return true;
                    }
                    sender.sendMessage(this.operations.revoke(args[1], args.length > 2 ? args[2] : AccessDatabase.WILDCARD));
                }
                case "list" -> sender.sendMessage(this.operations.list());
                case "attempts" -> {
                    int limit = 10;
                    if (args.length > 1) {
                        try {
                            limit = Integer.parseInt(args[1]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage("Usage: access attempts [count]");
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
            sender.sendMessage("Access database error, see console log");
            this.logger.error("Access command '{}' failed", args[0], e);
        }
        return true;
    }
}
