package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.track.Track;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-group and per-track join/quit messages and join sounds. The vanilla first-join/join/quit
 * templates in {@code join-messages} / {@code quit-messages} still apply as a fallback; this
 * service only kicks in when the player matches a group/track override.
 *
 * <p>Groups are matched by primary group first, then by track (highest weight wins), then
 * the global template (from ConnectionListener) is used.
 */
public final class JoinLeaveService {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final LPC plugin;
    private volatile boolean enabled;
    private volatile Entry defJoin;
    private volatile Entry defQuit;
    private volatile Entry firstJoin;
    private volatile Map<String, Entry> joinByGroup = Map.of();
    private volatile Map<String, Entry> quitByGroup = Map.of();
    private volatile Map<String, Entry> joinByTrack = Map.of();
    private volatile Map<String, Entry> quitByTrack = Map.of();

    public JoinLeaveService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("join-messages.per-group", false);

        this.defJoin = entryOrNull(c.getConfigurationSection("join-messages"));
        this.defQuit = entryOrNull(c.getConfigurationSection("quit-messages"));
        this.firstJoin = entryOrNull(c.getConfigurationSection("join-messages.first-join"));

        this.joinByGroup = loadMap(c.getConfigurationSection("join-messages.groups"));
        this.quitByGroup = loadMap(c.getConfigurationSection("quit-messages.groups"));
        this.joinByTrack = loadMap(c.getConfigurationSection("join-messages.tracks"));
        this.quitByTrack = loadMap(c.getConfigurationSection("quit-messages.tracks"));
    }

    public boolean isEnabled() { return enabled; }

    /** Render a join message for the player; returns null to suppress. */
    public Component renderJoin(Player player, boolean first) {
        if (!enabled) return null;
        Entry e;
        if (first && firstJoin != null) {
            e = firstJoin;
        } else {
            e = resolve(player, defJoin, joinByGroup, joinByTrack);
        }
        return renderEntry(player, e);
    }

    /** Render a quit message; returns null to suppress. */
    public Component renderQuit(Player player) {
        if (!enabled) return null;
        Entry e = resolve(player, defQuit, quitByGroup, quitByTrack);
        return renderEntry(player, e);
    }

    /** Play the configured join sound to the joining player (if any). */
    public void playJoinSound(Player player) {
        if (!enabled) return;
        Entry e;
        // Priority: group override > track override > default > firstJoin
        Entry resolved = resolve(player, defJoin, joinByGroup, joinByTrack);
        if (resolved == null) resolved = firstJoin;
        e = resolved;
        if (e == null || e.sound == null || e.sound.isEmpty()) return;
        try {
            player.playSound(player.getLocation(), e.sound, e.soundVolume, e.soundPitch);
        } catch (Exception ex) {
            // Unknown sound — ignore.
        }
    }

    private Entry resolve(Player player, Entry fallback, Map<String, Entry> byGroup,
                          Map<String, Entry> byTrack) {
        User user = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
        if (user == null) return fallback;
        String group = user.getPrimaryGroup();
        if (group != null) {
            Entry e = byGroup.get(group.toLowerCase());
            if (e != null && e.format != null && !e.format.isEmpty()) return e;
        }
        for (Map.Entry<String, Entry> t : byTrack.entrySet()) {
            Track track = LuckPermsProvider.get().getTrackManager().getTrack(t.getKey());
            if (track != null && group != null && track.containsGroup(group)
                    && t.getValue().format != null && !t.getValue().format.isEmpty()) {
                return t.getValue();
            }
        }
        return fallback;
    }

    private Component renderEntry(Player player, Entry e) {
        if (e == null || e.format == null || e.format.isEmpty()) return null;
        CachedMetaData meta = LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player);
        String prefix = meta.getPrefix() != null ? meta.getPrefix() : "";
        String suffix = meta.getSuffix() != null ? meta.getSuffix() : "";
        TagResolver resolver = TagResolver.builder()
                .resolver(Placeholder.parsed("name", player.getName()))
                .resolver(Placeholder.parsed("displayname",
                        PlainTextComponentSerializer.plainText().serialize(plugin.displayNameOf(player))))
                .resolver(Placeholder.parsed("prefix", prefix))
                .resolver(Placeholder.parsed("suffix", suffix))
                .resolver(Placeholder.parsed("world", player.getWorld().getName()))
                .resolver(Placeholder.component("display_name", plugin.displayNameOf(player)))
                .build();
        return MM.deserialize(e.format, resolver);
    }

    private Map<String, Entry> loadMap(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<String, Entry> out = new HashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection cs = section.getConfigurationSection(key);
            if (cs == null) continue;
            Entry e = entryOrNull(cs);
            if (e != null) out.put(key.toLowerCase(), e);
        }
        return Map.copyOf(out);
    }

    private Entry entryOrNull(ConfigurationSection cs) {
        if (cs == null) return null;
        String format = cs.getString("format");
        String sound = cs.getString("sound");
        float vol = (float) cs.getDouble("sound-volume", 1.0);
        float pitch = (float) cs.getDouble("sound-pitch", 1.0);
        boolean broadcastSound = cs.getBoolean("broadcast-sound", false);
        if (format == null && sound == null) return null;
        return new Entry(format, sound, vol, pitch, broadcastSound);
    }

    private record Entry(String format, String sound, float soundVolume, float soundPitch,
                         boolean broadcastSound) {}
}
