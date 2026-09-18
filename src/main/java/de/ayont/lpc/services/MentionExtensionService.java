package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import de.ayont.lpc.chat.PlayerMessages;
import de.ayont.lpc.events.LPCMentionEvent;
import de.ayont.lpc.hooks.VanishService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extends basic @player mention handling to support @staff and @everyone, complete with
 * permission checks, per-player cooldowns, and vanish awareness. Special-mention tokens are
 * matched against plain text and replaced on the component using a trusted cosmetic MiniMessage
 * parser, so they render with colour regardless of whether the player has chat-color permission.
 */
public final class MentionExtensionService {

    private final LPC plugin;
    private final MiniMessage cosmetic = PlayerMessages.colorParser(true);

    private final Map<UUID, Long> everyoneCooldowns = new ConcurrentHashMap<>();

    private volatile boolean everyoneEnabled;
    private volatile String everyonePermission;
    private volatile long everyoneCooldownSeconds;
    private volatile int maxEveryonePerMessage;
    private volatile String everyoneBypassPermission;
    private volatile String staffPermission;
    private volatile boolean staffEnabled;

    private static final Pattern EVERYONE = Pattern.compile("(?i)\\b@everyone\\b");
    private static final Pattern STAFF = Pattern.compile("(?i)\\b@staff\\b");

    public MentionExtensionService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.everyoneEnabled = c.getBoolean("mentions.everyone.enabled", true);
        this.everyonePermission = c.getString("mentions.everyone.permission", "lpc.mention.everyone");
        this.everyoneCooldownSeconds = c.getLong("mentions.everyone.cooldown", 60);
        this.maxEveryonePerMessage = c.getInt("mentions.everyone.max-per-message", 1);
        this.everyoneBypassPermission = c.getString("mentions.everyone.bypass-permission",
                "lpc.mention.everyone.bypass");
        this.staffEnabled = c.getBoolean("mentions.staff.enabled", true);
        this.staffPermission = c.getString("mentions.staff.permission", "lpc.staffchat");
    }

    public Component processSpecialMentions(Player sender, String rawText, Component message) {
        Component out = message;
        VanishService vanish = plugin.getVanishService();
        IgnoreService ignore = plugin.getIgnoreService();

        Set<Player> everyoneRecipients = new HashSet<>();
        Set<Player> staffRecipients = new HashSet<>();

        if (everyoneEnabled && countMatches(rawText, EVERYONE) > 0) {
            boolean hasPerm = sender.hasPermission(everyonePermission);
            boolean bypassCooldown = sender.hasPermission(everyoneBypassPermission);
            long now = System.currentTimeMillis();
            long cooldownMs = everyoneCooldownSeconds * 1000L;
            Long lastUse = everyoneCooldowns.get(sender.getUniqueId());
            boolean onCooldown = lastUse != null && now - lastUse < cooldownMs && !bypassCooldown;
            int allowedCount = Math.min(countMatches(rawText, EVERYONE), maxEveryonePerMessage);

            if (hasPerm && !onCooldown && allowedCount > 0) {
                everyoneCooldowns.put(sender.getUniqueId(), now);
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.equals(sender)) continue;
                    if (vanish.isVanished(p) && !sender.hasPermission("lpc.vanish.see")) continue;
                    if (ignore.isEnabled() && !ignore.hasBypass(sender) && ignore.isIgnoring(p, sender.getUniqueId())) continue;
                    if (p.hasPermission("lpc.mention.exempt")) continue;
                    everyoneRecipients.add(p);
                }
            }
        }

        if (staffEnabled && countMatches(rawText, STAFF) > 0 && sender.hasPermission("lpc.mention.staff.use")) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.equals(sender)) continue;
                boolean isStaff = p.hasPermission(staffPermission) || p.hasPermission("lpc.staffchat");
                if (!isStaff) continue;
                if (vanish.isVanished(p) && !sender.hasPermission("lpc.vanish.see")) continue;
                if (ignore.isEnabled() && !ignore.hasBypass(sender) && ignore.isIgnoring(p, sender.getUniqueId())) continue;
                if (p.hasPermission("lpc.mention.exempt")) continue;
                staffRecipients.add(p);
            }
        }

        if (everyoneEnabled) {
            Component everyoneTag = cosmetic.deserialize("<gold>@everyone</gold>");
            int[] replaced = {0};
            out = out.replaceText(b -> b.match(EVERYONE).replacement((mr, tb) -> {
                if (replaced[0] >= maxEveryonePerMessage) return tb;
                replaced[0]++;
                if (replaced[0] == 1) {
                    for (Player p : everyoneRecipients) {
                        LPCMentionEvent event = new LPCMentionEvent(true, sender, p, LPCMentionEvent.Type.EVERYONE);
                        plugin.getServer().getPluginManager().callEvent(event);
                        if (!event.isCancelled()) {
                            plugin.getNotificationService().notify(p, "everyone", Map.of("sender", sender.getName()));
                            plugin.getStatisticsService().incrementMention(sender, p, false, true);
                        }
                    }
                }
                return everyoneTag;
            }));
        }

        if (staffEnabled) {
            Component staffTag = cosmetic.deserialize("<gradient:#FED83D:#BE2086>@staff</gradient>");
            boolean[] notified = {false};
            out = out.replaceText(b -> b.match(STAFF).replacement((mr, tb) -> {
                if (!notified[0]) {
                    notified[0] = true;
                    for (Player p : staffRecipients) {
                        LPCMentionEvent event = new LPCMentionEvent(true, sender, p, LPCMentionEvent.Type.STAFF);
                        plugin.getServer().getPluginManager().callEvent(event);
                        if (!event.isCancelled()) {
                            plugin.getNotificationService().notify(p, "staff", Map.of("sender", sender.getName()));
                            plugin.getStatisticsService().incrementMention(sender, p, true, false);
                        }
                    }
                }
                return staffTag;
            }));
        }

        return out;
    }

    public long everyoneCooldownRemaining(Player player) {
        Long last = everyoneCooldowns.get(player.getUniqueId());
        if (last == null) return 0;
        long remain = (last + everyoneCooldownSeconds * 1000L) - System.currentTimeMillis();
        return Math.max(0, (remain + 999) / 1000);
    }

    private static int countMatches(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        int c = 0;
        while (m.find()) c++;
        return c;
    }
}
