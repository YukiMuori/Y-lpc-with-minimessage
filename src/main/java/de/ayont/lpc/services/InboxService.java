package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unread-message inbox for private messages.
 *
 * <p>When a player doesn't reply within {@code remind-delay-seconds} their incoming DMs
 * are buffered. Periodic and on-join reminders tell them how many unread DMs they have;
 * {@code /inbox} (or /messaggi, /mail, /lpc inbox) opens the list and marks all as read.
 * All user-visible text is customizable in config under {@code pm-inbox}.
 */
public final class InboxService {

    private static final int MAX_PER_PLAYER_FALLBACK = 50;

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
    private volatile String joinReminder;
    private volatile String periodicReminder;
    private volatile String emptyMessage;
    private volatile String header;
    private volatile String lineFormat;

    public InboxService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("pm-inbox.enabled", true);
        this.remindDelaySeconds = c.getInt("pm-inbox.remind-delay-seconds", 30);
        this.remindIntervalSeconds = c.getInt("pm-inbox.remind-interval-seconds", 60);
        this.maxPerPlayer = Math.max(1, c.getInt("pm-inbox.max-per-player", MAX_PER_PLAYER_FALLBACK));
        this.remindOnAfkReturn = c.getBoolean("pm-inbox.remind-on-afk-return", true);
        this.joinReminder = c.getString("pm-inbox.join-reminder",
                "<dark_gray>[<gradient:#B754F4:#FC00FF>LPC</gradient>] <yellow>Hai <white><count></white> PM non lett"
                        + "<plurale_i_o>. Usa <white>/inbox</white>.");
        this.periodicReminder = c.getString("pm-inbox.remind-message",
                "<dark_gray>[<gradient:#B754F4:#FC00FF>LPC</gradient>] <yellow>Hai <white><count></white> PM non lett"
                        + "<plurale_i_o>. <white>/inbox</white>");
        this.emptyMessage = c.getString("pm-inbox.empty-message",
                "<dark_gray>[<gradient:#B754F4:#FC00FF>LPC</gradient>] <yellow>Non hai messaggi non letti.");
        this.header = c.getString("pm-inbox.header",
                "<dark_gray>[<gradient:#B754F4:#FC00FF>LPC</gradient>] <yellow>Messaggi non letti <gray>(<count>):");
        this.lineFormat = c.getString("pm-inbox.line-format",
                "<dark_gray>- <white><from></white> <gray>(<ago>): <white><msg>");
    }

    public boolean isEnabled() { return enabled; }
    public String getEmptyMessage() { return emptyMessage; }
    public String getHeader() { return header; }
    public String getLineFormat() { return lineFormat; }

    public void enqueue(UUID recipientId, String senderName, String message) {
        if (!enabled) return;
        List<UnreadMessage> list = inbox.computeIfAbsent(recipientId, u -> new ArrayList<>());
        synchronized (list) {
            list.add(new UnreadMessage(senderName, truncate(message, 80), System.currentTimeMillis()));
            while (list.size() > maxPerPlayer) list.remove(0);
        }
    }

    public int unreadCount(Player player) {
        List<UnreadMessage> list = inbox.get(player.getUniqueId());
        if (list == null) return 0;
        synchronized (list) { return list.size(); }
    }

    public List<UnreadMessage> drain(Player player) {
        List<UnreadMessage> list = inbox.remove(player.getUniqueId());
        if (list == null) return List.of();
        synchronized (list) { return List.copyOf(list); }
    }

    public void markFromAsRead(UUID recipientId, String senderName) {
        List<UnreadMessage> list = inbox.get(recipientId);
        if (list == null) return;
        synchronized (list) { list.removeIf(m -> m.from.equalsIgnoreCase(senderName)); }
    }

    public void onJoin(Player player) {
        if (!enabled) return;
        int n = unreadCount(player);
        if (n <= 0) return;
        plugin.send(player, mm.deserialize(applyCount(joinReminder, n)));
    }

    public void tick() {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            int n = unreadCount(p);
            if (n <= 0) continue;
            Long last = lastReminder.get(p.getUniqueId());
            long interval = Math.max(30L, remindIntervalSeconds) * 1000L;
            if (last != null && now - last < interval) continue;
            List<UnreadMessage> list = inbox.get(p.getUniqueId());
            if (list == null) continue;
            long oldest;
            synchronized (list) {
                oldest = list.isEmpty() ? now : list.get(0).receivedAtEpochMs;
            }
            if (now - oldest < remindDelaySeconds * 1000L) continue;
            lastReminder.put(p.getUniqueId(), now);
            plugin.send(p, mm.deserialize(applyCount(periodicReminder, n)));
        }
    }

    public void onQuit(UUID playerId) {
        lastReminder.remove(playerId);
    }

    private static String applyCount(String template, int n) {
        return template
                .replace("<count>", Integer.toString(n))
                .replace("<plurale_i_o>", n == 1 ? "o" : "i")
                .replace("{count}", Integer.toString(n))
                .replace("{plurale_i_o}", n == 1 ? "o" : "i");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
