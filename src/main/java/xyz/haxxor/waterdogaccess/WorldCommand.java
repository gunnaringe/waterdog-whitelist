package xyz.haxxor.waterdogaccess;

import dev.waterdog.waterdogpe.command.Command;
import dev.waterdog.waterdogpe.command.CommandSender;
import dev.waterdog.waterdogpe.command.CommandSettings;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;

/**
 * A replacement for WaterdogPE's built-in /server: that command's own permission gate
 * (waterdog.command.server.permission) reliably rejects every player with "You do not have the
 * permission to execute this command", even when granted both via permissions_default and the
 * per-gamertag permissions map - looks like a bug in the pinned dev-snapshot build we run, not a
 * config mistake (verified the exact permission string against the compiled class; verified
 * subscribePermissions() actually runs). No permission set here at all (Command's default is ""),
 * so CommandSender.hasPermission() short-circuits true for everyone - actual per-world
 * enforcement still happens the same way it always did, in WaterdogAccess's own
 * ServerPreConnectEvent handler, which fires for this connect() call exactly like any other.
 */
public class WorldCommand extends Command {

    public WorldCommand() {
        super("world", CommandSettings.builder()
                .setDescription("Switch to another world")
                .setUsageMessage("world <name>")
                .build());
    }

    @Override
    public boolean onExecute(CommandSender sender, String alias, String[] args) {
        if (args.length < 1 || !sender.isPlayer()) {
            return false;
        }
        ServerInfo server = sender.getProxy().getServerInfo(args[0]);
        if (server == null) {
            sender.sendMessage("§cWorld not found: " + args[0]);
            return true;
        }
        ((ProxiedPlayer) sender).connect(server);
        return true;
    }
}
