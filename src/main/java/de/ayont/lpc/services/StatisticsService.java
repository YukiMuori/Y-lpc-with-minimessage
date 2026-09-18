package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import de.ayont.lpc.database.DatabaseService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Tallies chat statistics into the SQLite database. Counters are incremented asynchronously
 * so the chat pipeline never blocks on DB I/O.
 */
public final class StatisticsService {

    private final LPC plugin;
    private final DatabaseService db;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private volatile boolean enabled;

    public StatisticsService(LPC plugin, DatabaseService db) {
        this.plugin = plugin;
        this.db = db;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("statistics.enabled", true);
    }

    public boolean isEnabled() { return enabled && db.isAvailable(); }

    // ── Increment helpers (no-op when disabled) ───────────────────────────

    public void incrementMessage(Player player) {
        if (!isEnabled()) return;
        db.incrementGlobal("messages_sent", 1);
        db.incrementPlayer(player.getUniqueId(), player.getName(), "messages_sent", 1);
    }

    public void incrementBlocked(Player player) {
        if (!isEnabled()) return;
        db.incrementGlobal("messages_blocked", 1);
        db.incrementPlayer(player.getUniqueId(), player.getName(), "messages_blocked", 1);
    }

    public void incrementDmSent(Player player) {
        if (!isEnabled()) return;
        db.incrementGlobal("dm_sent", 1);
        db.incrementPlayer(player.getUniqueId(), player.getName(), "dm_sent", 1);
    }

    public void incrementDmReceived(Player player) {
        if (!isEnabled()) return;
        db.incrementPlayer(player.getUniqueId(), player.getName(), "dm_received", 1);
    }

    public void incrementMention(Player sender, Player receiver, boolean staff, boolean everyone) {
        if (!isEnabled()) return;
        db.incrementGlobal("mentions", 1);
        if (staff) db.incrementGlobal("mention_staff", 1);
        if (everyone) db.incrementGlobal("mention_everyone", 1);
        db.incrementPlayer(sender.getUniqueId(), sender.getName(), "mentions_made", 1);
        db.incrementPlayer(receiver.getUniqueId(), receiver.getName(), "mentions_received", 1);
    }

    public void incrementGlyph(Player player, String glyphName) {
        if (!isEnabled()) return;
        db.incrementGlobal("glyphs_used", 1);
        db.incrementPlayer(player.getUniqueId(), player.getName(), "glyphs_used", 1);
    }

    public void incrementLink(Player player) {
        if (!isEnabled()) return;
        db.incrementGlobal("links_sent", 1);
    }

    public void incrementStaffMessage(Player player) {
        if (!isEnabled()) return;
        db.incrementGlobal("staff_messages", 1);
    }

    public void incrementDiscordMessage() {
        if (!isEnabled()) return;
        db.incrementGlobal("discord_messages", 1);
    }

    // ── Display ───────────────────────────────────────────────────────────

    /** Sends a player their own stats. */
    public void showOwnStats(Player viewer) {
        if (!isEnabled()) {
            plugin.send(viewer, mm.deserialize("<red>Statistics are disabled."));
            return;
        }
        fetchPlayerStats(viewer.getUniqueId(), viewer.getName()).thenAccept(stats -> {
            plugin.getScheduler().runOnEntity(viewer, () -> sendPlayerStats(viewer, viewer.getName(), stats));
        });
    }

    /** Sends stats for another player (requires lpc.stats.others). */
    public void showOtherStats(Player viewer, String targetName) {
        if (!viewer.hasPermission("lpc.stats.others")) {
            plugin.send(viewer, mm.deserialize("<red>You don't have permission to view others' stats."));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(targetName);
        UUID uuid;
        String name;
        if (target != null) {
            uuid = target.getUniqueId();
            name = target.getName();
        } else {
            // Lookup by last-known name via async DB query (best-effort)
            uuid = null;
            name = targetName;
            plugin.send(viewer, mm.deserialize("<red>Player not found online."));
            return;
        }
        fetchPlayerStats(uuid, name).thenAccept(stats -> {
            plugin.getScheduler().runOnEntity(viewer, () -> sendPlayerStats(viewer, name, stats));
        });
    }

    private CompletableFuture<Map<String, Long>> fetchPlayerStats(UUID uuid, String name) {
        return db.queryAsync(conn -> {
            try (var ps = conn.prepareStatement("SELECT messages_sent,dm_sent,dm_received," +
                    "mentions_made,mentions_received,glyphs_used,messages_blocked FROM player_stats WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Map.of(
                                "messages_sent", rs.getLong(1),
                                "dm_sent", rs.getLong(2),
                                "dm_received", rs.getLong(3),
                                "mentions_made", rs.getLong(4),
                                "mentions_received", rs.getLong(5),
                                "glyphs_used", rs.getLong(6),
                                "messages_blocked", rs.getLong(7)
                        );
                    }
                }
            }
            return Map.<String, Long>of(
                    "messages_sent", 0L, "dm_sent", 0L, "dm_received", 0L,
                    "mentions_made", 0L, "mentions_received", 0L,
                    "glyphs_used", 0L, "messages_blocked", 0L);
        });
    }

    private void sendPlayerStats(Player viewer, String name, Map<String, Long> s) {
        Component header = mm.deserialize("<gradient:#FED83D:#BE2086>LPC Stats</gradient> <gray>— <white><name></white>",
                Placeholder.unparsed("name", name));
        plugin.send(viewer, header);
        plugin.send(viewer, line("messages_sent", "Chat messages", s.get("messages_sent")));
        plugin.send(viewer, line("dm_sent", "DMs sent", s.get("dm_sent")));
        plugin.send(viewer, line("dm_received", "DMs received", s.get("dm_received")));
        plugin.send(viewer, line("mentions_made", "Mentions", s.get("mentions_made")));
        plugin.send(viewer, line("mentions_received", "Times mentioned", s.get("mentions_received")));
        plugin.send(viewer, line("glyphs_used", "Glyphs/emoji", s.get("glyphs_used")));
        plugin.send(viewer, line("messages_blocked", "Messages blocked", s.get("messages_blocked")));
    }

    private Component line(String id, String label, Long val) {
        long v = val == null ? 0L : val;
        return mm.deserialize("<dark_gray>- <gray><label>: <white><value>",
                Placeholder.unparsed("label", label),
                Placeholder.unparsed("value", Long.toString(v)));
    }
}
