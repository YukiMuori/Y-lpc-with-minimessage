package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralised notifications: plays a sound and/or shows an action-bar/title to a player
 * when an event (mention, DM, ...) occurs. Individual notification types can be disabled
 * globally via config and toggled per player via /lpc notifications.
 */
public final class NotificationService {

    /** Set of notification types toggled OFF per player (default empty = all enabled). */
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
                if (List.of("enabled", "staff-chat", "discord-message").contains(key)) continue;
                ConfigurationSection ns = section.getConfigurationSection(key);
                if (ns == null) continue;
                String sound = ns.getString("sound", "");
                float volume = (float) ns.getDouble("volume", 1.0);
                float pitch = (float) ns.getDouble("pitch", 1.0);
                String actionbar = ns.getString("actionbar", "");
                String title = ns.getString("title", null);
                String subtitle = ns.getString("subtitle", null);
                map.put(key, new NotificationProfile(sound, volume, pitch, actionbar, title, subtitle));
            }
        }
        // Staff chat notifications (if configured)
        ConfigurationSection sc = config.getConfigurationSection("notifications.staff-chat");
        if (sc != null) {
            map.put("staff-chat", new NotificationProfile(
                    sc.getString("sound", ""),
                    (float) sc.getDouble("volume", 1.0),
                    (float) sc.getDouble("pitch", 1.0),
                    sc.getString("actionbar", ""),
                    sc.getString("title", null),
                    sc.getString("subtitle", null)));
        }
        this.profiles = Map.copyOf(map);
    }

    public boolean isEnabled() { return enabled; }

    /** Returns true if the given player has disabled the specified notification type. */
    public boolean isDisabled(Player player, String type) {
        Set<String> set = disabledByPlayer.get(player.getUniqueId());
        return set != null && set.contains(type);
    }

    /** Toggle a single notification type for the player; returns the new state (true = enabled). */
    public boolean toggle(Player player, String type) {
        Set<String> set = disabledByPlayer.computeIfAbsent(player.getUniqueId(), u -> ConcurrentHashMap.newKeySet());
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
        plugin.getScheduler().runOnEntity(target, () -> {
            if (p.sound != null && !p.sound.isEmpty()) {
                try {
                    target.playSound(target.getLocation(), p.sound, p.volume, p.pitch);
                } catch (Exception ignored) {
                    // Invalid sound name — skip silently.
                }
            }
            if (plugin.isPaper() && p.actionbar != null && !p.actionbar.isEmpty()) {
                Component bar = resolve(p.actionbar, placeholders);
                target.sendActionBar(bar);
            }
        });
    }

    private Component resolve(String template, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return miniMessage.deserialize(template);
        }
        TagResolver[] resolvers = placeholders.entrySet().stream()
                .map(e -> Placeholder.unparsed(e.getKey(), e.getValue()))
                .toArray(TagResolver[]::new);
        return miniMessage.deserialize(template, resolvers);
    }

    public Set<String> getAvailableTypes() { return profiles.keySet(); }

    public Set<String> getDisabled(Player player) {
        Set<String> s = disabledByPlayer.get(player.getUniqueId());
        return s == null ? Set.of() : Set.copyOf(s);
    }

    private record NotificationProfile(String sound, float volume, float pitch,
                                       String actionbar, String title, String subtitle) {}
}
