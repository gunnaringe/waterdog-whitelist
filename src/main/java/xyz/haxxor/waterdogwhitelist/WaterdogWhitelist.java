package xyz.haxxor.waterdogwhitelist;

import dev.waterdog.waterdogpe.event.defaults.PlayerLoginEvent;
import dev.waterdog.waterdogpe.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;

public class WaterdogWhitelist extends Plugin {

    private WhitelistDatabase database;
    private CliWatcher cliWatcher;
    private Thread cliThread;

    @Override
    public void onEnable() {
        try {
            this.database = new WhitelistDatabase(new File(this.getDataFolder(), "whitelist.db"));
        } catch (SQLException e) {
            this.getLogger().error("Could not open whitelist database, denying all logins!", e);
            return;
        }

        WhitelistOperations operations = new WhitelistOperations(this.database);

        this.getProxy().getEventManager().subscribe(PlayerLoginEvent.class, this::onLogin);
        this.getProxy().getCommandMap().registerCommand(new WhitelistCommand(operations, this.getLogger()));

        this.cliWatcher = new CliWatcher(new File(this.getDataFolder(), "cli"), operations, this.getLogger());
        this.cliThread = new Thread(this.cliWatcher, "WaterdogWhitelist-CLI");
        this.cliThread.setDaemon(true);
        this.cliThread.start();

        this.getLogger().info("WaterdogWhitelist enabled - deny by default, use /whitelist or the admin scripts to manage");
    }

    @Override
    public void onDisable() {
        if (this.cliWatcher != null) {
            this.cliWatcher.stop();
        }
        if (this.database != null) {
            try {
                this.database.close();
            } catch (SQLException e) {
                this.getLogger().error("Could not close whitelist database", e);
            }
        }
    }

    private void onLogin(PlayerLoginEvent event) {
        var player = event.getPlayer();
        String xuid = player.getXuid();
        String gamertag = player.getName();
        String address = player.getAddress().getAddress().getHostAddress();

        boolean allowed;
        try {
            allowed = this.database.isAllowed(xuid);
        } catch (SQLException e) {
            this.getLogger().error("Whitelist lookup failed for {} ({}), denying login", gamertag, xuid, e);
            allowed = false;
        }

        try {
            this.database.recordAttempt(xuid, gamertag, address, allowed);
        } catch (SQLException e) {
            this.getLogger().error("Could not record join attempt for {} ({})", gamertag, xuid, e);
        }

        if (allowed) {
            this.getLogger().info("Login allowed: {} ({}) from {}", gamertag, xuid, address);
        } else {
            event.setCancelled(true);
            event.setCancelReason("§cYou are not whitelisted on this server.");
            this.getLogger().warn("Login denied: {} ({}) from {} - not whitelisted", gamertag, xuid, address);
        }
    }
}
