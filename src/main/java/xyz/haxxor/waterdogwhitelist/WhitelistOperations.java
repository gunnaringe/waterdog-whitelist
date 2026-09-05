package xyz.haxxor.waterdogwhitelist;

import java.sql.SQLException;
import java.util.List;

/**
 * The four whitelist actions, shared between the in-game /whitelist command and the
 * file-based CLI (CliWatcher) so admin scripts and console commands stay in sync.
 */
public class WhitelistOperations {

    private final WhitelistDatabase database;

    public WhitelistOperations(WhitelistDatabase database) {
        this.database = database;
    }

    public String add(String xuid, String gamertag) throws SQLException {
        this.database.addToWhitelist(xuid, gamertag);
        return "Whitelisted " + (gamertag != null ? gamertag + " (" + xuid + ")" : xuid);
    }

    public String remove(String xuid) throws SQLException {
        boolean removed = this.database.removeFromWhitelist(xuid);
        return removed ? "Removed " + xuid + " from the whitelist" : xuid + " wasn't whitelisted";
    }

    public String list() throws SQLException {
        List<WhitelistDatabase.WhitelistEntry> entries = this.database.listWhitelist();
        if (entries.isEmpty()) {
            return "Whitelist is empty";
        }
        StringBuilder result = new StringBuilder("Whitelisted players:");
        for (WhitelistDatabase.WhitelistEntry entry : entries) {
            result.append('\n').append(" - ").append(entry.gamertag() != null ? entry.gamertag() : "?")
                    .append(" (").append(entry.xuid()).append("), added ").append(entry.addedAt());
        }
        return result.toString();
    }

    public String attempts(int limit) throws SQLException {
        List<WhitelistDatabase.AttemptEntry> attempts = this.database.recentAttempts(limit);
        if (attempts.isEmpty()) {
            return "No join attempts recorded yet";
        }
        StringBuilder result = new StringBuilder("Recent join attempts:");
        for (WhitelistDatabase.AttemptEntry attempt : attempts) {
            String status = attempt.allowed() ? "ALLOWED" : "DENIED";
            result.append('\n').append(" - ").append(status).append(' ').append(attempt.gamertag())
                    .append(" (").append(attempt.xuid()).append(") from ").append(attempt.address())
                    .append(" at ").append(attempt.attemptedAt());
        }
        return result.toString();
    }
}
