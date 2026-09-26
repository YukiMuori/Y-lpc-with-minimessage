package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unread-message inbox for private messages.
 *
 * <p>When a player is offline or ignores the notification window (no reply within
 * {@link #remindDelaySeconds} seconds) their incoming DMs are buffered here. On join,
 * on returning from AFK, and periodically they get a reminder telling them how many
 * unread DMs they have; {@code /lpc inbox} opens the list and marks messages as read.
 *
 * <p>Storage is in-memory for simplicity (DMs aren't meant to be permanent mail); cap
 * is enforced per recipient so it can't balloon.
 */
public final class InboxService {

    private static final int MAX_PER_PLAYER = 50;

    public record UnreadMessage(String from, String preview, long receivedAtEpochMs) {}

    private final LPC plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, List<UnreadMessage>> inbox = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReminder = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile int remindDelaySeconds;
    private volatile int remindIntervalSeconds;
    private volatile int maxPerPlayer;
    private volatile boolean remindOnAfkReturn;

    public InboxService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("pm-inbox.enabled", true);
        this.remindDelaySeconds = c.getInt("pm-inbox.remind-delay-seconds", 30);
        this.remindIntervalSeconds = c.getInt("pm-inbox.remind-interval-seconds", 60);
        this.maxPerPlayer = Math.max(1, c.getInt("pm-inbox.max-per-player", MAX_PER_PLAYER));
        this.remindOnAfkReturn = c.getBoolean("pm-inbox.remind-on-afk-return", true);
    }

    public boolean isEnabled() { return enabled; }

    /** Queue a PM as unread for a (possibly online) player. */
    public void enqueue(UUID recipientId, String senderName, String message) {
        if (!enabled) return;
        List<UnreadMessage> list = inbox.computeIfAbsent(recipientId, u -> new ArrayList<>());
        synchronized (list) {
            list.add(new UnreadMessage(senderName, truncate(message, 80), System.currentTimeMillis()));
            while (list.size() > maxPerPlayer) list.remove(0);
        }
    }

    /** How many unread messages the player has. */
    public int unreadCount(Player player) {
        List<UnreadMessage> list = inbox.get(player.getUniqueId());
        if (list == null) return 0;
        synchronized (list) { return list.size(); }
    }

    /** Drain the unread queue (return and clear). */
    public List<UnreadMessage> drain(Player player) {
        List<UnreadMessage> list = inbox.remove(player.getUniqueId());
        if (list == null) return List.of();
        synchronized (list) { return List.copyOf(list); }
    }

    /** Mark all messages from a specific sender as read (e.g. when receiver replies to them). */
    public void markFromAsRead(UUID recipientId, String senderName) {
        List<UnreadMessage> list = inbox.get(recipientId);
        if (list == null) return;
        synchronized (list) { list.removeIf(m -> m.from.equalsIgnoreCase(senderName)); }
    }

    /** Called when a player joins to deliver a reminder if they have unread mail. */
    public void onJoin(Player player) {
        if (!enabled) return;
        int n = unreadCount(player);
        if (n > 0) {
            plugin.send(player, mm.deserialize(
                    "<dark_gray>[<gradient:#B754F4:#FC00FF>LPC</gradient>] <yellow>Hai <white>" + n
                            + "</white> messagg" + (n == 1 ? "io" : "i")
                            + " privat" + (n == 1 ? "o" : "i")
                            + " non lett" + (n == 1 ? "o" : "i")
                            + ". Usa <white>/lpc inbox</white> per leggerli."));
        }
    }

    /** Called periodically to remind players who haven't responded. */
    public void tick() {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            int n = unreadCount(p);
            if (n <= 0) continue;
            Long last = lastReminder.get(p.getUniqueId());
            long interval = Math.max(30L, remindIntervalSeconds) * 1000L;
            if (last != null && now - last < interval) continue;
            // Only remind if the oldest unread is older than remindDelaySeconds
            List<UnreadMessage> list = inbox.get(p.getUniqueId());
            if (list == null) continue;
            long oldest;
            synchronized (list) {
                oldest = list.isEmpty() ? now : list.get(0).receivedAtEpochMs;
            }
            if (now - oldest < remindDelaySeconds * 1000L) continue;
            lastReminder.put(p.getUniqueId(), now);
            plugin.send(p, mm.deserialize(
                    "<dark_gray>[<gradient:#B754F4:#FC00FF>LPC</gradient>] <yellow>Hai <white>" + n
                            + "</white> PM non lett" + (n == 1 ? "o" : "i")
                            + ". <white>/lpc inbox</white>"));
        }
    }

    public void onQuit(UUID playerId) {
        lastReminder.remove(playerId);
        // Inbox is intentionally kept across relogs so offline DMs are visible next join.
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
