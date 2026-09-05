package xyz.haxxor.waterdogaccess;

import dev.waterdog.waterdogpe.event.defaults.PlayerDisconnectedEvent;
import dev.waterdog.waterdogpe.event.defaults.PlayerLoginEvent;
import dev.waterdog.waterdogpe.event.defaults.ServerConnectedEvent;
import dev.waterdog.waterdogpe.event.defaults.ServerPreConnectEvent;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import dev.waterdog.waterdogpe.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WaterdogAccess extends Plugin {

    private static final int API_PORT = 8181;

    private AccessDatabase database;
    private ApiServer apiServer;
    private final Map<UUID, MenuHandler> menus = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        try {
            this.database = new AccessDatabase(new File(this.getDataFolder(), "access.db"));
        } catch (SQLException e) {
            this.getLogger().error("Could not open access database, denying all logins!", e);
            return;
        }

        AccessOperations operations = new AccessOperations(this.database);

        this.getProxy().getEventManager().subscribe(PlayerLoginEvent.class, this::onLogin);
        this.getProxy().getEventManager().subscribe(ServerPreConnectEvent.class, this::onPreConnect);
        this.getProxy().getEventManager().subscribe(ServerConnectedEvent.class, this::onServerConnected);
        this.getProxy().getEventManager().subscribe(PlayerDisconnectedEvent.class, this::onDisconnect);
        this.getProxy().setJoinHandler(new JoinHandler(this.database, this.getLogger()));
        this.getProxy().getCommandMap().registerCommand(new AccessCommand(operations, this.getLogger()));
        this.getProxy().getCommandMap().registerCommand(new WorldCommand());

        try {
            this.apiServer = new ApiServer(API_PORT, operations, this.getLogger());
            this.apiServer.start();
        } catch (IOException e) {
            this.getLogger().error("Could not start the admin API on port {}, use /access from the console instead", API_PORT, e);
        }

        this.getLogger().info("WaterdogAccess enabled - deny by default, use /access or the admin scripts to manage");
    }

    @Override
    public void onDisable() {
        if (this.apiServer != null) {
            this.apiServer.stop();
        }
        if (this.database != null) {
            try {
                this.database.close();
            } catch (SQLException e) {
                this.getLogger().error("Could not close access database", e);
            }
        }
    }

    private void onLogin(PlayerLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String xuid = player.getXuid();
        String gamertag = player.getName();
        String address = player.getAddress().getAddress().getHostAddress();

        boolean allowed;
        try {
            allowed = this.database.hasAnyAccess(xuid);
        } catch (SQLException e) {
            this.getLogger().error("Access lookup failed for {} ({}), denying login", gamertag, xuid, e);
            allowed = false;
        }

        try {
            this.database.recordAttempt(xuid, gamertag, address, allowed);
        } catch (SQLException e) {
            this.getLogger().error("Could not record join attempt for {} ({})", gamertag, xuid, e);
        }

        if (allowed) {
            this.getLogger().info("Login allowed: {} ({}) from {}", gamertag, xuid, address);
            MenuHandler menu = new MenuHandler(player, this.database);
            this.menus.put(player.getUniqueId(), menu);
            player.getPluginPacketHandlers().add(menu);
        } else {
            event.setCancelled(true);
            event.setCancelReason("§cYou don't have access to this server.");
            this.getLogger().warn("Login denied: {} ({}) from {} - no access anywhere", gamertag, xuid, address);
        }
    }

    /**
     * "lobby" has no real hub to explore - the moment a player lands there (initial join, or a
     * later /world lobby) we replace it with a native Bedrock form listing whatever worlds this
     * player currently has access to, so picking one is a controller-friendly button press
     * instead of typing a command. See MenuHandler for why the handler is registered once at
     * login rather than re-added here.
     */
    private void onServerConnected(ServerConnectedEvent event) {
        if (!"lobby".equals(event.getTargetServer().getServerName())) {
            return;
        }
        MenuHandler menu = this.menus.get(event.getPlayer().getUniqueId());
        if (menu != null) {
            menu.sendMenu();
        }
    }

    private void onDisconnect(PlayerDisconnectedEvent event) {
        this.menus.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Fires for every downstream connection attempt - the initial one after login (source null)
     * and every later /server transfer alike. Cancelling a transfer safely leaves the player on
     * their current server (WaterdogPE just drops the pending target); cancelling the initial
     * connection would leave them in limbo instead, which is why JoinHandler is what actually
     * keeps that path safe - this is a defense-in-depth backstop, not the primary gate for it.
     */
    private void onPreConnect(ServerPreConnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String world = event.getTargetServer().getServerName();
        try {
            if (!this.database.isAllowed(player.getXuid(), world)) {
                event.setCancelled(true);
                player.sendMessage("§cYou don't have access to " + world);
                this.getLogger().warn("Blocked {} ({}) from connecting to {} - no access", player.getName(), player.getXuid(), world);
            }
        } catch (SQLException e) {
            // Fail closed: an unreadable database should not silently grant a transfer.
            event.setCancelled(true);
            this.getLogger().error("Access lookup failed for {} while connecting to {}, blocking", player.getXuid(), world, e);
        }
    }
}
