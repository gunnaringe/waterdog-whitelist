package dev.gunnaringe.waterdogwhitelist;

import dev.waterdog.waterdogpe.command.Command;
import dev.waterdog.waterdogpe.command.CommandSender;
import dev.waterdog.waterdogpe.command.CommandSettings;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.List;

/**
 * Console-only by design: permission is left unset in the proxy's permissions/permissions_default,
 * and ConsoleCommandSender.hasPermission() always returns true, so only whoever can attach to the
 * pod's console can manage the whitelist.
 */
public class WhitelistCommand extends Command {

    private final WhitelistDatabase database;
    private final Logger logger;

    public WhitelistCommand(WhitelistDatabase database, Logger logger) {
        super("whitelist", CommandSettings.builder()
                .setDescription("Manage the join whitelist")
                .setPermission("waterdogwhitelist.admin")
                .setUsageMessage("whitelist <add|remove|list|attempts> [xuid] [gamertag]")
                .build());
        this.database = database;
        this.logger = logger;
    }

    @Override
    public boolean onExecute(CommandSender sender, String alias, String[] args) {
        if (args.length == 0) {
            return false;
        }

        try {
            switch (args[0].toLowerCase()) {
                case "add" -> this.add(sender, args);
                case "remove" -> this.remove(sender, args);
                case "list" -> this.list(sender);
                case "attempts" -> this.attempts(sender, args);
                default -> {
                    return false;
                }
            }
        } catch (SQLException e) {
            sender.sendMessage("§cWhitelist database error, see console log");
            this.logger.error("Whitelist command '{}' failed", args[0], e);
        }
        return true;
    }

    private void add(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: whitelist add <xuid> [gamertag]");
            return;
        }
        String xuid = args[1];
        String gamertag = args.length > 2 ? args[2] : null;
        this.database.addToWhitelist(xuid, gamertag);
        sender.sendMessage("§aWhitelisted " + (gamertag != null ? gamertag + " (" + xuid + ")" : xuid));
    }

    private void remove(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: whitelist remove <xuid>");
            return;
        }
        boolean removed = this.database.removeFromWhitelist(args[1]);
        sender.sendMessage(removed ? "§aRemoved " + args[1] + " from the whitelist" : "§c" + args[1] + " wasn't whitelisted");
    }

    private void list(CommandSender sender) throws SQLException {
        List<WhitelistDatabase.WhitelistEntry> entries = this.database.listWhitelist();
        if (entries.isEmpty()) {
            sender.sendMessage("§eWhitelist is empty");
            return;
        }
        sender.sendMessage("§eWhitelisted players:");
        for (WhitelistDatabase.WhitelistEntry entry : entries) {
            sender.sendMessage(" - " + (entry.gamertag() != null ? entry.gamertag() : "?") + " (" + entry.xuid() + "), added " + entry.addedAt());
        }
    }

    private void attempts(CommandSender sender, String[] args) throws SQLException {
        int limit = 10;
        if (args.length > 1) {
            try {
                limit = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§cUsage: whitelist attempts [count]");
                return;
            }
        }
        List<WhitelistDatabase.AttemptEntry> attempts = this.database.recentAttempts(limit);
        if (attempts.isEmpty()) {
            sender.sendMessage("§eNo join attempts recorded yet");
            return;
        }
        sender.sendMessage("§eRecent join attempts:");
        for (WhitelistDatabase.AttemptEntry attempt : attempts) {
            String status = attempt.allowed() ? "§aALLOWED" : "§cDENIED";
            sender.sendMessage(" - " + status + " §r" + attempt.gamertag() + " (" + attempt.xuid() + ") from "
                    + attempt.address() + " at " + attempt.attemptedAt());
        }
    }
}
