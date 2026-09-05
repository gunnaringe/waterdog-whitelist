package xyz.haxxor.waterdogaccess;

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
 * Single SQLite connection guarded by a lock. Login/transfer volume on this proxy is low
 * (a handful of players), so there's no need for a connection pool.
 *
 * Access is keyed by (xuid, world), where either half may be the wildcard "*": (*, bob) means
 * anyone can join bob, (someXuid, *) means that player can join any current or future world,
 * and (*, *) opens the whole proxy to anyone.
 */
public class AccessDatabase implements AutoCloseable {

    public static final String WILDCARD = "*";

    public record AccessEntry(String xuid, String world, String gamertag, String addedAt) {
    }

    public record AttemptEntry(String xuid, String gamertag, String address, String attemptedAt, boolean allowed) {
    }

    private final Object lock = new Object();
    private final Connection connection;

    public AccessDatabase(File databaseFile) throws SQLException {
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
                    CREATE TABLE IF NOT EXISTS access (
                        xuid TEXT NOT NULL,
                        world TEXT NOT NULL,
                        gamertag TEXT,
                        added_at TEXT NOT NULL,
                        PRIMARY KEY (xuid, world)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS attempts (
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

    /** Login-time gate: does this xuid have access to *something* - a specific grant, or is any world public? */
    public boolean hasAnyAccess(String xuid) throws SQLException {
        synchronized (this.lock) {
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "SELECT 1 FROM access WHERE xuid = ? OR xuid = ? LIMIT 1")) {
                statement.setString(1, xuid);
                statement.setString(2, WILDCARD);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        }
    }

    /** Per-world gate: can this xuid enter this specific world? Covers both wildcard axes. */
    public boolean isAllowed(String xuid, String world) throws SQLException {
        synchronized (this.lock) {
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "SELECT 1 FROM access WHERE (xuid = ? OR xuid = ?) AND (world = ? OR world = ?) LIMIT 1")) {
                statement.setString(1, xuid);
                statement.setString(2, WILDCARD);
                statement.setString(3, world);
                statement.setString(4, WILDCARD);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        }
    }

    public void recordAttempt(String xuid, String gamertag, String address, boolean allowed) throws SQLException {
        synchronized (this.lock) {
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "INSERT INTO attempts (xuid, gamertag, address, attempted_at, allowed) VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, xuid);
                statement.setString(2, gamertag);
                statement.setString(3, address);
                statement.setString(4, Instant.now().toString());
                statement.setInt(5, allowed ? 1 : 0);
                statement.executeUpdate();
            }
        }
    }

    public void grant(String xuid, String world, String gamertag) throws SQLException {
        synchronized (this.lock) {
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "INSERT INTO access (xuid, world, gamertag, added_at) VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT(xuid, world) DO UPDATE SET gamertag = excluded.gamertag")) {
                statement.setString(1, xuid);
                statement.setString(2, world);
                statement.setString(3, gamertag);
                statement.setString(4, Instant.now().toString());
                statement.executeUpdate();
            }
        }
    }

    public boolean revoke(String xuid, String world) throws SQLException {
        synchronized (this.lock) {
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "DELETE FROM access WHERE xuid = ? AND world = ?")) {
                statement.setString(1, xuid);
                statement.setString(2, world);
                return statement.executeUpdate() > 0;
            }
        }
    }

    public List<AccessEntry> list() throws SQLException {
        synchronized (this.lock) {
            List<AccessEntry> entries = new ArrayList<>();
            try (Statement statement = this.connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT xuid, world, gamertag, added_at FROM access ORDER BY added_at")) {
                while (result.next()) {
                    entries.add(new AccessEntry(result.getString("xuid"), result.getString("world"),
                            result.getString("gamertag"), result.getString("added_at")));
                }
            }
            return entries;
        }
    }

    public List<AttemptEntry> recentAttempts(int limit) throws SQLException {
        synchronized (this.lock) {
            List<AttemptEntry> entries = new ArrayList<>();
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "SELECT xuid, gamertag, address, attempted_at, allowed FROM attempts ORDER BY id DESC LIMIT ?")) {
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
