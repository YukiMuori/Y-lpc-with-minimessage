package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralised notifications. Each notification type (mention, dm, slow, etc.) can deliver
 * to any combination of channels: sound, action-bar, title, chat message and/or boss-bar.
 * Individual types can be disabled per-player via /lpc notifications.
 */
public final class NotificationService {

    /** Set of notification types toggled OFF per player (empty = all enabled). */
    private final Map<UUID, Set<String>> disabledByPlayer = new ConcurrentHashMap<>();

    private final LPC plugin;
    private volatile boolean enabled;
    private volatile Map<String, NotificationProfile> profiles = Map.of();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public NotificationService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("notifications.enabled", true);
        Map<String, NotificationProfile> map = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("notifications");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (List.of("enabled").contains(key)) continue;
                ConfigurationSection ns = section.getConfigurationSection(key);
                if (ns == null) continue;
                map.put(key, loadProfile(ns));
            }
        }
        this.profiles = Map.copyOf(map);
    }

    private NotificationProfile loadProfile(ConfigurationSection ns) {
        String sound = ns.getString("sound", "");
        float volume = (float) ns.getDouble("volume", 1.0);
        float pitch = (float) ns.getDouble("pitch", 1.0);
        String chat = ns.getString("chat", null);
        String actionbar = ns.getString("actionbar", "");
        String title = ns.getString("title", null);
        String subtitle = ns.getString("subtitle", null);
        // Boss bar
        boolean bossbar = ns.getBoolean("bossbar.enabled", false);
        String bossText = ns.getString("bossbar.text", null);
        String bossColor = ns.getString("bossbar.color", "red");
        String bossOverlay = ns.getString("bossbar.overlay", "progress");
        float bossProgress = (float) ns.getDouble("bossbar.progress", 1.0);
        double bossSeconds = ns.getDouble("bossbar.seconds", 2.5);
        // Title timings
        int titleFadeIn = ns.getInt("title-fade-in", 10);
        int titleStay = ns.getInt("title-stay", 40);
        int titleFadeOut = ns.getInt("title-fade-out", 10);
        return new NotificationProfile(
                sound, volume, pitch, chat, actionbar, title, subtitle,
                bossbar, bossText, bossColor, bossOverlay, bossProgress, bossSeconds,
                titleFadeIn, titleStay, titleFadeOut);
    }

    public boolean isEnabled() { return enabled; }

    /** Returns true if the given player has disabled the specified notification type. */
    public boolean isDisabled(Player player, String type) {
        Set<String> set = disabledByPlayer.get(player.getUniqueId());
        return set != null && set.contains(type);
    }

    /** Toggle a single notification type for the player; returns the new state (true = enabled). */
    public boolean toggle(Player player, String type) {
        Set<String> set = disabledByPlayer.computeIfAbsent(player.getUniqueId(),
                u -> ConcurrentHashMap.newKeySet());
        if (set.contains(type)) { set.remove(type); return true; }
        set.add(type); return false;
    }

    /** Toggle ALL notifications for the player; returns true if now enabled. */
    public boolean toggleAll(Player player) {
        Set<String> set = disabledByPlayer.get(player.getUniqueId());
        if (set == null || set.isEmpty()) {
            set = ConcurrentHashMap.newKeySet();
            set.add("ALL");
            disabledByPlayer.put(player.getUniqueId(), set);
            return false;
        }
        set.clear();
        disabledByPlayer.remove(player.getUniqueId());
        return true;
    }

    public boolean isAllDisabled(Player player) {
        Set<String> set = disabledByPlayer.get(player.getUniqueId());
        return set != null && set.contains("ALL");
    }

    /** Fire a notification to the target player. */
    public void notify(Player target, String type, Map<String, String> placeholders) {
        if (!enabled || !profiles.containsKey(type)) return;
        if (isAllDisabled(target) || isDisabled(target, type)) return;
        NotificationProfile p = profiles.get(type);
        plugin.getScheduler().runOnEntity(target, () -> deliver(target, p, placeholders));
    }

    /**
     * Send an admin/error alert to a player via the "system.alert" notification profile
     * (e.g. "write slower", "you are muted"). Falls back to chat if no profile exists.
     */
    public void alert(Player target, String fallbackMiniMessage) {
        if (target == null || !target.isOnline()) return;
        NotificationProfile p = profiles.get("system.alert");
        if (p == null) {
            plugin.send(target, miniMessage.deserialize(fallbackMiniMessage));
            return;
        }
        plugin.getScheduler().runOnEntity(target, () -> deliver(target, p, Map.of("text", fallbackMiniMessage)));
    }

    private void deliver(Player target, NotificationProfile p, Map<String, String> placeholders) {
        if (!target.isOnline()) return;
        // Sound
        if (p.sound != null && !p.sound.isEmpty()) {
            try { target.playSound(target.getLocation(), p.sound, p.volume, p.pitch); }
            catch (Exception ignored) {}
        }
        // Action bar
        if (plugin.isPaper() && p.actionbar != null && !p.actionbar.isEmpty()) {
            target.sendActionBar(resolve(p.actionbar, placeholders));
        }
        // Chat
        if (p.chat != null && !p.chat.isEmpty()) {
            plugin.send(target, resolve(p.chat, placeholders));
        }
        // Title
        if (plugin.isPaper() && p.title != null && !p.title.isEmpty()) {
            Component t = resolve(p.title, placeholders);
            Component s = p.subtitle != null && !p.subtitle.isEmpty()
                    ? resolve(p.subtitle, placeholders) : Component.empty();
            Title.Times times = Title.Times.times(
                    Duration.ofMillis(p.titleFadeIn * 50L),
                    Duration.ofMillis(p.titleStay * 50L),
                    Duration.ofMillis(p.titleFadeOut * 50L));
            target.showTitle(Title.title(t, s, times));
        }
        // Boss bar
        if (plugin.isPaper() && p.bossbar && p.bossText != null && !p.bossText.isEmpty()) {
            Component text = resolve(p.bossText, placeholders);
            BossBar bar = BossBar.bossBar(text, p.bossProgress,
                    BossBarService.color(p.bossColor), BossBarService.overlay(p.bossOverlay));
            target.showBossBar(bar);
            long delayTicks = Math.max(10L, (long) (p.bossSeconds * 20L));
            plugin.getScheduler().runDelayed(() -> {
                if (target.isOnline()) target.hideBossBar(bar);
            }, delayTicks);
        }
    }

    private Component resolve(String template, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return miniMessage.deserialize(template);
        }
        TagResolver[] resolvers = placeholders.entrySet().stream()
                .map(e -> Placeholder.unparsed(e.getKey(), e.getValue() == null ? "" : e.getValue()))
                .toArray(TagResolver[]::new);
        return miniMessage.deserialize(template, resolvers);
    }

    public Set<String> getAvailableTypes() { return profiles.keySet(); }

    public Set<String> getDisabled(Player player) {
        Set<String> s = disabledByPlayer.get(player.getUniqueId());
        return s == null ? Set.of() : Set.copyOf(s);
    }

    @SuppressWarnings("checkstyle:RecordComponentNumber")
    private record NotificationProfile(
            String sound, float volume, float pitch,
            String chat, String actionbar, String title, String subtitle,
            boolean bossbar, String bossText, String bossColor, String bossOverlay,
            float bossProgress, double bossSeconds,
            int titleFadeIn, int titleStay, int titleFadeOut) {}
}
