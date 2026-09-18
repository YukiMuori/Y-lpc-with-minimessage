package de.ayont.lpc.services;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player toggle state for chat features (private messages, staff-chat mode,
 * social-spy, etc.). Stored in-memory only (reset on disconnect / plugin reload),
 * consistent with a lightweight chat plugin.
 */
public final class PlayerSettingsService {

    private final Set<UUID> pmDisabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffChatMode = ConcurrentHashMap.newKeySet();
    private final Set<UUID> socialSpy = ConcurrentHashMap.newKeySet();

    /** Clear any data for a player who has left. */
    public void onQuit(UUID uuid) {
        pmDisabled.remove(uuid);
        staffChatMode.remove(uuid);
        // social-spy is a staff command — intentionally kept across relogs.
    }

    // ── Private messages ──────────────────────────────────────────────────

    public boolean isPmDisabled(org.bukkit.entity.Player player) {
        return pmDisabled.contains(player.getUniqueId());
    }

    /** Toggle PMs for the player; returns the new state (true = disabled). */
    public boolean togglePm(org.bukkit.entity.Player player) {
        UUID id = player.getUniqueId();
        if (pmDisabled.contains(id)) { pmDisabled.remove(id); return false; }
        pmDisabled.add(id); return true;
    }

    // ── Staff chat mode ───────────────────────────────────────────────────

    public boolean isInStaffChat(org.bukkit.entity.Player player) {
        return staffChatMode.contains(player.getUniqueId());
    }

    public boolean toggleStaffChat(org.bukkit.entity.Player player) {
        UUID id = player.getUniqueId();
        if (staffChatMode.contains(id)) { staffChatMode.remove(id); return false; }
        staffChatMode.add(id); return true;
    }

    // ── Social spy ────────────────────────────────────────────────────────

    public boolean isSocialSpy(org.bukkit.entity.Player player) {
        return socialSpy.contains(player.getUniqueId());
    }

    public boolean toggleSocialSpy(org.bukkit.entity.Player player) {
        UUID id = player.getUniqueId();
        if (socialSpy.contains(id)) { socialSpy.remove(id); return false; }
        socialSpy.add(id); return true;
    }
}
