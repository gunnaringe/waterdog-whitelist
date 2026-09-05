package xyz.haxxor.waterdogaccess;

import dev.waterdog.waterdogpe.ProxyServer;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import dev.waterdog.waterdogpe.network.connection.handler.IJoinHandler;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;

/**
 * Picks the first server in config.yml's priorities list that the joining player actually has
 * access to, instead of WaterdogPE's DefaultJoinHandler, which always picks priorities[0]
 * regardless of who's connecting. Returning null (no allowed server found) makes the proxy
 * disconnect the player with its own clean built-in message rather than connecting them
 * somewhere they shouldn't be - see ProxiedPlayer's "waterdog.no.initial.server" handling.
 */
public class JoinHandler implements IJoinHandler {

    private final AccessDatabase database;
    private final Logger logger;

    public JoinHandler(AccessDatabase database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    @Override
    public ServerInfo determineServer(ProxiedPlayer player) {
        ProxyServer proxy = ProxyServer.getInstance();
        for (String name : proxy.getConfiguration().getPriorities()) {
            try {
                if (this.database.isAllowed(player.getXuid(), name)) {
                    return proxy.getServerInfo(name);
                }
            } catch (SQLException e) {
                this.logger.error("Access lookup failed for {} while picking an initial server", player.getXuid(), e);
                return null;
            }
        }
        return null;
    }
}
