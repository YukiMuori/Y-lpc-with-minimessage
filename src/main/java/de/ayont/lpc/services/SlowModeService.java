package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat slow mode. Players must wait {@code seconds} between messages (global chat only —
 * private messages and staff chat are not gated).
 */
public final class SlowModeService {

    private final LPC plugin;
    private final Map<UUID, Long> nextMessageAt = new ConcurrentHashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();

    private volatile boolean enabled;
    private volatile int currentSeconds;
    private volatile String waitMessage;

    public SlowModeService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("slow-mode.enabled", true);
        this.currentSeconds = c.getInt("slow-mode.default-seconds", 0);
        this.waitMessage = c.getString("slow-mode.wait-message",
                "<red>Please wait <yellow>{seconds}s</yellow> before chatting again.");
    }

    public boolean isEnabled() { return enabled; }
    public int getCurrentSeconds() { return currentSeconds; }

    /** Set the slow-mode delay; 0 disables. Returns the new value. */
    public int setSlowMode(int seconds) {
        this.currentSeconds = Math.max(0, seconds);
        if (seconds == 0) nextMessageAt.clear();
        return this.currentSeconds;
    }

    /**
     * Check whether a player may send a chat message now.
     * @return null if allowed; a Component to send the player as an error if not allowed
     */
    public Component check(Player player) {
        if (!enabled || currentSeconds <= 0) return null;
        if (player.hasPermission("lpc.slowmode.bypass")) return null;
        long now = System.currentTimeMillis();
        long wait = currentSeconds * 1000L;
        Long next = nextMessageAt.get(player.getUniqueId());
        if (next != null && now < next) {
            long remain = Math.max(1, (next - now + 999) / 1000);
            return mm.deserialize(waitMessage, Placeholder.unparsed("seconds", Long.toString(remain)));
        }
        nextMessageAt.put(player.getUniqueId(), now + wait);
        return null;
    }
}
