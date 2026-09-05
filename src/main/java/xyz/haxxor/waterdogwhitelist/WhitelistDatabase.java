package xyz.haxxor.waterdogwhitelist;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Single SQLite connection guarded by a lock. Login volume on this proxy is low
 * (a handful of players), so there's no need for a connection pool.
 */
public class WhitelistDatabase implements AutoCloseable {

    public record WhitelistEntry(String xuid, String gamertag, String addedAt) {
    }

    public record AttemptEntry(String xuid, String gamertag, String address, String attemptedAt, boolean allowed) {
    }

    private final Object lock = new Object();
    private final Connection connection;

    public WhitelistDatabase(File databaseFile) throws SQLException {
        try {
            // DriverManager's own ServiceLoader-based discovery runs once at JVM boot, using the
            // system classloader - it never sees drivers shaded into a jar that WaterdogPE's plugin
            // loader loads later with its own classloader. Forcing the class to load here runs
            // org.sqlite.JDBC's static initializer, which self-registers with DriverManager directly.
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("sqlite-jdbc driver not on the classpath", e);
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = this.connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS whitelist (
                        xuid TEXT PRIMARY KEY,
                        gamertag TEXT,
                        added_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS join_attempts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        xuid TEXT NOT NULL,
                        gamertag TEXT,
                        address TEXT,
                        attempted_at TEXT NOT NULL,
                        allowed INTEGER NOT NULL
                    )
                    """);
        }
    }

    public boolean isAllowed(String xuid) throws SQLException {
        synchronized (this.lock) {
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "SELECT 1 FROM whitelist WHERE xuid = ?")) {
                statement.setString(1, xuid);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        }
    }

    public void recordAttempt(String xuid, String gamertag, String address, boolean allowed) throws SQLException {
        synchronized (this.lock) {
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "INSERT INTO join_attempts (xuid, gamertag, address, attempted_at, allowed) VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, xuid);
                statement.setString(2, gamertag);
                statement.setString(3, address);
                statement.setString(4, Instant.now().toString());
                statement.setInt(5, allowed ? 1 : 0);
                statement.executeUpdate();
            }
        }
    }

    public void addToWhitelist(String xuid, String gamertag) throws SQLException {
        synchronized (this.lock) {
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "INSERT INTO whitelist (xuid, gamertag, added_at) VALUES (?, ?, ?) " +
                            "ON CONFLICT(xuid) DO UPDATE SET gamertag = excluded.gamertag")) {
                statement.setString(1, xuid);
                statement.setString(2, gamertag);
                statement.setString(3, Instant.now().toString());
                statement.executeUpdate();
            }
        }
    }

    public boolean removeFromWhitelist(String xuid) throws SQLException {
        synchronized (this.lock) {
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "DELETE FROM whitelist WHERE xuid = ?")) {
                statement.setString(1, xuid);
                return statement.executeUpdate() > 0;
            }
        }
    }

    public List<WhitelistEntry> listWhitelist() throws SQLException {
        synchronized (this.lock) {
            List<WhitelistEntry> entries = new ArrayList<>();
            try (Statement statement = this.connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT xuid, gamertag, added_at FROM whitelist ORDER BY added_at")) {
                while (result.next()) {
                    entries.add(new WhitelistEntry(result.getString("xuid"), result.getString("gamertag"),
                            result.getString("added_at")));
                }
            }
            return entries;
        }
    }

    public List<AttemptEntry> recentAttempts(int limit) throws SQLException {
        synchronized (this.lock) {
            List<AttemptEntry> entries = new ArrayList<>();
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "SELECT xuid, gamertag, address, attempted_at, allowed FROM join_attempts " +
                            "ORDER BY id DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        entries.add(new AttemptEntry(result.getString("xuid"), result.getString("gamertag"),
                                result.getString("address"), result.getString("attempted_at"),
                                result.getInt("allowed") == 1));
                    }
                }
            }
            return entries;
        }
    }

    @Override
    public void close() throws SQLException {
        synchronized (this.lock) {
            this.connection.close();
        }
    }
}
