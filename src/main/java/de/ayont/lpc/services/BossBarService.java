package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight boss-bar utility. Lets any part of the plugin display a transient boss bar
 * to a player (e.g. "Slow mode active: wait 3s") without leaving stale bars around.
 *
 * <p>When a new bar is shown to a player, any previous bar pushed by this service is
 * overwritten so alert stacking doesn't happen. Bars auto-expire after a configurable
 * duration.
 */
public final class BossBarService {

    private final LPC plugin;
    private final Map<UUID, BossBar> active = new ConcurrentHashMap<>();

    public BossBarService(LPC plugin) {
        this.plugin = plugin;
    }

    /** Parse a colour name ("pink"/"blue"/"red"/"green"/"yellow"/"purple"/"white"). */
    public static BossBar.Color color(String name) {
        if (name == null) return BossBar.Color.RED;
        return switch (name.toLowerCase()) {
            case "pink" -> BossBar.Color.PINK;
            case "blue" -> BossBar.Color.BLUE;
            case "red" -> BossBar.Color.RED;
            case "green" -> BossBar.Color.GREEN;
            case "yellow" -> BossBar.Color.YELLOW;
            case "purple" -> BossBar.Color.PURPLE;
            case "white" -> BossBar.Color.WHITE;
            default -> BossBar.Color.RED;
        };
    }

    /** Parse overlay name ("progress"/"notched_6"/"notched_10"/"notched_12"/"notched_20"). */
    public static BossBar.Overlay overlay(String name) {
        if (name == null) return BossBar.Overlay.PROGRESS;
        return switch (name.toLowerCase().replace('-', '_')) {
            case "progress" -> BossBar.Overlay.PROGRESS;
            case "notched_6" -> BossBar.Overlay.NOTCHED_6;
            case "notched_10" -> BossBar.Overlay.NOTCHED_10;
            case "notched_12" -> BossBar.Overlay.NOTCHED_12;
            case "notched_20" -> BossBar.Overlay.NOTCHED_20;
            default -> BossBar.Overlay.PROGRESS;
        };
    }

    /** Show a boss bar to {@code player} for {@code seconds} seconds (0 = until manually hidden). */
    public void show(Player player, String miniMessageText, BossBar.Color color,
                     BossBar.Overlay overlay, float progress, double seconds) {
        if (player == null || !player.isOnline()) return;
        Component name = MiniMessage.miniMessage().deserialize(miniMessageText);
        BossBar bar = BossBar.bossBar(name, Math.max(0f, Math.min(1f, progress)), color, overlay);

        plugin.getScheduler().runOnEntity(player, () -> {
            BossBar prev = active.put(player.getUniqueId(), bar);
            if (prev != null) player.hideBossBar(prev);
            player.showBossBar(bar);
        });

        if (seconds > 0) {
            long delayTicks = Math.max(1L, (long) (seconds * 20L));
            plugin.getScheduler().runDelayed(() -> hide(player, bar), delayTicks);
        }
    }

    public void hide(Player player, BossBar bar) {
        if (player == null) return;
        plugin.getScheduler().runOnEntity(player, () -> {
            active.remove(player.getUniqueId(), bar);
            if (player.isOnline()) player.hideBossBar(bar);
        });
    }

    /** Hide every bar we are currently showing a player — called on quit. */
    public void clear(Player player) {
        BossBar bar = active.remove(player.getUniqueId());
        if (bar != null && player.isOnline()) {
            plugin.getScheduler().runOnEntity(player, () -> player.hideBossBar(bar));
        }
    }

    public void reload() {
        // Nothing to reload; bars are transient.
    }
}
