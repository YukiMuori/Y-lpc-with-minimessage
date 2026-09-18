package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import de.ayont.lpc.events.LPCIgnoreEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player ignore lists. A player can add another player to their ignore list, which (depending
 * on configuration) blocks: global chat, private messages, mentions, and notifications from that
 * player. Players with the configured bypass permission can always be seen.
 */
public final class IgnoreService {

    private final LPC plugin;
    private final Map<UUID, Set<UUID>> ignores = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile boolean blockChat;
    private volatile boolean blockPMs;
    private volatile boolean blockMentions;
    private volatile boolean blockNotifications;
    private volatile String bypassPermission;

    public IgnoreService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("ignore.enabled", true);
        this.blockChat = c.getBoolean("ignore.blocks.chat", true);
        this.blockPMs = c.getBoolean("ignore.blocks.private-messages", true);
        this.blockMentions = c.getBoolean("ignore.blocks.mentions", true);
        this.blockNotifications = c.getBoolean("ignore.blocks.notifications", true);
        this.bypassPermission = c.getString("ignore.bypass-permission", "lpc.ignore.bypass");
    }

    public boolean isEnabled() { return enabled; }
    public boolean blocksChat() { return blockChat; }
    public boolean blocksPrivateMessages() { return blockPMs; }
    public boolean blocksMentions() { return blockMentions; }
    public boolean blocksNotifications() { return blockNotifications; }

    /** @return true if {@code player} is currently ignoring {@code target}. */
    public boolean isIgnoring(Player player, UUID target) {
        if (!enabled) return false;
        Set<UUID> set = ignores.get(player.getUniqueId());
        return set != null && set.contains(target);
    }

    /** @return true if the target has bypass permission to never be ignored. */
    public boolean hasBypass(Player target) {
        return bypassPermission != null && !bypassPermission.isEmpty() && target.hasPermission(bypassPermission);
    }

    /** Toggle the target in the player's ignore list.
     *  @return true if now ignored, false if now un-ignored
     */
    public boolean toggle(Player player, UUID targetId, String targetName) {
        Set<UUID> set = ignores.computeIfAbsent(player.getUniqueId(), u -> ConcurrentHashMap.newKeySet());
        boolean added;
        LPCIgnoreEvent.Action action;
        if (set.contains(targetId)) {
            set.remove(targetId);
            added = false;
            action = LPCIgnoreEvent.Action.REMOVE;
        } else {
            set.add(targetId);
            added = true;
            action = LPCIgnoreEvent.Action.ADD;
        }
        LPCIgnoreEvent event = new LPCIgnoreEvent(player, targetId, targetName, action);
        plugin.getServer().getPluginManager().callEvent(event);
        return added;
    }

    /** Returns the snapshot of ignored UUIDs for the player. */
    public Set<UUID> getIgnored(Player player) {
        Set<UUID> set = ignores.get(player.getUniqueId());
        return set == null ? Set.of() : Set.copyOf(set);
    }
}
