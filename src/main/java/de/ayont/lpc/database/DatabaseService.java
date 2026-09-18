package de.ayont.lpc.database;

import de.ayont.lpc.LPC;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Encapsulates the SQLite connection, schema bootstrap and async execution for LPC statistics.
 * <p>
 * All queries are executed on a single dedicated background thread (no connection pool needed for
 * one writer at a time) and NEVER on the server main thread. Failure is logged but never throws
 * into the chat pipeline.
 */
public final class DatabaseService implements AutoCloseable {

    private static final int SCHEMA_VERSION = 1;

    private final LPC plugin;
    private final File dbFile;
    private final ExecutorService executor;
    private volatile Connection connection;

    public DatabaseService(LPC plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "data.db");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LPC-SQLite");
            t.setDaemon(true);
            return t;
        });
    }

    /** Opens the database (creating the file if needed) and bootstraps the schema. */
    public void initialize() {
        try {
            if (!plugin.getDataFolder().exists()) {
                //noinspection ResultOfMethodCallIgnored
                plugin.getDataFolder().mkdirs();
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA foreign_keys=ON");
                createSchema(stmt);
            }
            plugin.getLogger().info("Statistics database initialized: " + dbFile.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize statistics database", e);
            connection = null;
        }
    }

    private void createSchema(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS lpc_meta (" +
                "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        stmt.execute("CREATE TABLE IF NOT EXISTS player_stats (" +
                "uuid TEXT PRIMARY KEY," +
                "name TEXT NOT NULL," +
                "messages_sent INTEGER DEFAULT 0," +
                "dm_sent INTEGER DEFAULT 0," +
                "dm_received INTEGER DEFAULT 0," +
                "mentions_made INTEGER DEFAULT 0," +
                "mentions_received INTEGER DEFAULT 0," +
                "glyphs_used INTEGER DEFAULT 0," +
                "messages_blocked INTEGER DEFAULT 0," +
                "updated_at INTEGER DEFAULT 0)");
        stmt.execute("CREATE TABLE IF NOT EXISTS global_stats (" +
                "key TEXT PRIMARY KEY, value INTEGER DEFAULT 0)");
        stmt.execute("INSERT OR IGNORE INTO lpc_meta (key, value) VALUES ('schema_version', '" + SCHEMA_VERSION + "')");
        // Initialize global counters
        for (String k : new String[]{"messages_sent", "messages_blocked", "dm_sent", "mentions",
                "mention_staff", "mention_everyone", "glyphs_used", "links_sent",
                "staff_messages", "discord_messages"}) {
            stmt.execute("INSERT OR IGNORE INTO global_stats (key, value) VALUES ('" + k + "', 0)");
        }
    }

    public boolean isAvailable() { return connection != null; }

    /** Submits a blocking JDBC operation to the async worker. */
    public CompletableFuture<Void> executeAsync(ThrowingConsumer<Connection> action) {
        if (connection == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try {
                Connection c = connection;
                synchronized (c) {
                    action.accept(c);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Async DB operation failed", e);
            }
        }, executor);
    }

    /** Submits a blocking JDBC read to the async worker and returns its future result. */
    public <T> CompletableFuture<T> queryAsync(ThrowingFunction<Connection, T> action) {
        if (connection == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> {
            try {
                Connection c = connection;
                synchronized (c) {
                    return action.apply(c);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Async DB query failed", e);
                return null;
            }
        }, executor);
    }

    public void incrementGlobal(String key, long amount) {
        executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO global_stats (key, value) VALUES (?, ?) " +
                            "ON CONFLICT(key) DO UPDATE SET value = value + ?")) {
                ps.setString(1, key);
                ps.setLong(2, amount);
                ps.setLong(3, amount);
                ps.executeUpdate();
            }
        });
    }

    public void incrementPlayer(java.util.UUID uuid, String name, String column, long amount) {
        executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO player_stats (uuid, name, " + column + ", updated_at) " +
                            "VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET " + column + " = " + column + " + ?, " +
                            "name = ?, updated_at = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setLong(3, amount);
                ps.setLong(4, System.currentTimeMillis());
                ps.setLong(5, amount);
                ps.setString(6, name);
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error closing database", e);
        }
    }

    @FunctionalInterface public interface ThrowingConsumer<T> { void accept(T t) throws Exception; }
    @FunctionalInterface public interface ThrowingFunction<T, R> { R apply(T t) throws Exception; }
}
