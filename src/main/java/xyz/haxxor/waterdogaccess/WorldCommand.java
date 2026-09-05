package xyz.haxxor.waterdogaccess;

import dev.waterdog.waterdogpe.ProxyServer;
import dev.waterdog.waterdogpe.command.Command;
import dev.waterdog.waterdogpe.command.CommandSender;
import dev.waterdog.waterdogpe.command.CommandSettings;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParam;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;

import java.util.LinkedHashMap;
import java.util.Map;

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

    private final ProxyServer proxy;

    public WorldCommand(ProxyServer proxy) {
        super("world", CommandSettings.builder()
                .setDescription("Switch to another world")
                .setUsageMessage("world <name>")
                .build());
        this.proxy = proxy;
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

    /**
     * Declares "name" as a soft enum of the configured server names instead of a plain string, so
     * the client renders it as a tab-completable dropdown while typing the command - the same
     * mechanism vanilla commands like /gamemode use. Built once at registration (proxy startup),
     * so a server added to config.yml needs a proxy restart to show up in the suggestions, same as
     * it already needs one to be usable at all.
     */
    @Override
    protected CommandOverloadData[] buildCommandOverloads() {
        Map<String, java.util.Set<org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint>> values = new LinkedHashMap<>();
        for (ServerInfo server : this.proxy.getServers()) {
            values.put(server.getServerName(), java.util.Set.of());
        }

        CommandParamData param = new CommandParamData();
        param.setName("name");
        param.setOptional(false);
        param.setType(CommandParam.STRING);
        param.setEnumData(new CommandEnumData("WorldName", values, true));

        return new CommandOverloadData[]{new CommandOverloadData(false, new CommandParamData[]{param})};
    }
}
