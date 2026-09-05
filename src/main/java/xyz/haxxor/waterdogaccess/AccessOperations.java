package xyz.haxxor.waterdogaccess;

import java.sql.SQLException;
import java.util.List;

/**
 * The four access actions, shared between the in-game /access command and the admin HTTP API
 * (ApiServer) so scripts and console commands stay in sync.
 */
public class AccessOperations {

    private final AccessDatabase database;

    public AccessOperations(AccessDatabase database) {
        this.database = database;
    }

    public String grant(String xuid, String world, String gamertag) throws SQLException {
        this.database.grant(xuid, world, gamertag);
        return "Granted " + (gamertag != null ? gamertag + " (" + xuid + ")" : xuid) + " access to " + world;
    }

    public String revoke(String xuid, String world) throws SQLException {
        boolean removed = this.database.revoke(xuid, world);
        return removed ? "Revoked " + xuid + "'s access to " + world : xuid + " didn't have access to " + world;
    }

    public String list() throws SQLException {
        List<AccessDatabase.AccessEntry> entries = this.database.list();
        if (entries.isEmpty()) {
            return "No access granted yet";
        }
        StringBuilder result = new StringBuilder("Granted access:");
        for (AccessDatabase.AccessEntry entry : entries) {
            result.append('\n').append(" - ").append(entry.xuid())
                    .append(entry.gamertag() != null ? " (" + entry.gamertag() + ")" : "")
                    .append(" -> ").append(entry.world())
                    .append(", added ").append(entry.addedAt());
        }
        return result.toString();
    }

    public String attempts(int limit) throws SQLException {
        List<AccessDatabase.AttemptEntry> attempts = this.database.recentAttempts(limit);
        if (attempts.isEmpty()) {
            return "No join attempts recorded yet";
        }
        StringBuilder result = new StringBuilder("Recent join attempts:");
        for (AccessDatabase.AttemptEntry attempt : attempts) {
            String status = attempt.allowed() ? "ALLOWED" : "DENIED";
            result.append('\n').append(" - ").append(status).append(' ').append(attempt.gamertag())
                    .append(" (").append(attempt.xuid()).append(") from ").append(attempt.address())
                    .append(" at ").append(attempt.attemptedAt());
        }
        return result.toString();
    }
}
