package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat slow mode. Players must wait {@code seconds} between messages (global chat only).
 * Error messages are routed through {@link NotificationService#alert} so server owners can
 * pick the channel: chat, actionbar, title or bossbar.
 */
public final class SlowModeService {

    private final LPC plugin;
    private final Map<UUID, Long> nextMessageAt = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile int currentSeconds;

    public SlowModeService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("slow-mode.enabled", true);
        this.currentSeconds = c.getInt("slow-mode.default-seconds", 0);
    }

    public boolean isEnabled() { return enabled; }
    public int getCurrentSeconds() { return currentSeconds; }

    public int setSlowMode(int seconds) {
        this.currentSeconds = Math.max(0, seconds);
        if (seconds == 0) nextMessageAt.clear();
        return this.currentSeconds;
    }

    /**
     * @return true if the message is BLOCKED (an error was already sent to the player via
     *         notificationService.alert); false if the player may chat.
     */
    public boolean checkAndBlock(Player player) {
        if (!enabled || currentSeconds <= 0) return false;
        if (player.hasPermission("lpc.slowmode.bypass")) return false;
        long now = System.currentTimeMillis();
        long wait = currentSeconds * 1000L;
        Long next = nextMessageAt.get(player.getUniqueId());
        if (next != null && now < next) {
            long remain = Math.max(1, (next - now + 999) / 1000);
            plugin.getNotificationService().notify(player, "slowmode",
                    Map.of("seconds", Long.toString(remain)));
            return true;
        }
        nextMessageAt.put(player.getUniqueId(), now + wait);
        return false;
    }
}
