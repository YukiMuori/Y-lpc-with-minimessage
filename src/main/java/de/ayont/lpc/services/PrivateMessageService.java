package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import de.ayont.lpc.chat.ChatFormatService;
import de.ayont.lpc.chat.PlayerMessages;
import de.ayont.lpc.discord.DiscordService;
import de.ayont.lpc.events.LPCPrivateMessageEvent;
import de.ayont.lpc.hooks.VanishService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements /msg, /w, /tell, /r. Remembers the last conversation partner per player so
 * that /reply works across sessions while both players remain online.
 * <p>
 * Rules enforced: offline/vanished/ignored/disabled-message targets are rejected. Messages
 * are rendered with the same safe player-message parser as global chat, including emoji,
 * URLs, and glyph replacements (delegated to the caller where applicable).
 */
public final class PrivateMessageService {

    private final LPC plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Last sender to reply TO (i.e. the person you received a msg from).
    private final Map<UUID, UUID> replyTarget = new ConcurrentHashMap<>();
    // The person YOU last sent TO (for recipients' /r to find you).
    private final Map<UUID, UUID> lastSentTo = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile String senderFormat;
    private volatile String receiverFormat;
    private volatile String socialSpyFormat;
    private volatile boolean socialSpyEnabled;
    private volatile String noReplyMessage;
    private volatile String playerNotFoundMessage;
    private volatile String playerOfflineMessage;
    private volatile String ignoredMessage;
    private volatile String disabledPmMessage;

    public PrivateMessageService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("private-messages.enabled", true);
        this.senderFormat = c.getString("private-messages.format-sender",
                "<gray>[<gold>You</gold> <gold>→</gold> <white>{receiver}</white>]</gray> <white>{message}");
        this.receiverFormat = c.getString("private-messages.format-receiver",
                "<gray>[<white>{sender}</white> <gold>→</gold> <gold>You</gold>]</gray> <white>{message}");
        this.socialSpyEnabled = c.getBoolean("social-spy.enabled", true);
        this.socialSpyFormat = c.getString("social-spy.format",
                "<dark_gray>[SocialSpy]</dark_gray> <gray>{sender} <dark_gray>→</dark_gray> {receiver}: <white>{message}</white>");
        this.noReplyMessage = c.getString("private-messages.no-reply", "<red>No player to reply to.");
        this.playerNotFoundMessage = c.getString("private-messages.player-not-found", "<red>Player not found.");
        this.playerOfflineMessage = c.getString("private-messages.player-offline", "<red>{name} is not online.");
        this.ignoredMessage = c.getString("private-messages.ignored", "<red>You cannot message that player.");
        this.disabledPmMessage = c.getString("private-messages.disabled", "<red>That player has private messages disabled.");
    }

    public boolean isEnabled() { return enabled; }

    /** @return the UUID of the last player who messaged this player, or null. */
    public UUID getReplyTarget(UUID player) {
        return replyTarget.get(player);
    }

    /**
     * Send a private message.
     * @return true if the message was sent
     */
    public boolean sendMessage(Player sender, Player target, String rawMessage) {
        if (!enabled) return false;

        VanishService vanish = plugin.getVanishService();
        IgnoreService ignore = plugin.getIgnoreService();

        // Permission checks
        if (!sender.hasPermission("lpc.msg")) {
            plugin.send(sender, mini("<red>You don't have permission to send private messages."));
            return false;
        }

        // Vanish visibility: a non-vanished player may NOT message a vanished player they
        // cannot see. If the sender cannot see the target (target vanished & sender lacks
        // vanish-override permission), treat target as offline.
        if (vanish.isVanished(target) && !sender.hasPermission("lpc.vanish.see")) {
            plugin.send(sender, mini(playerOfflineMessage,
                    Placeholder.unparsed("name", target.getName())));
            return false;
        }

        // Respect ignore
        if (ignore.isEnabled() && !ignore.hasBypass(sender) && ignore.isIgnoring(target, sender.getUniqueId())) {
            plugin.send(sender, mini(ignoredMessage));
            return false;
        }
        if (ignore.isEnabled() && !ignore.hasBypass(sender) && ignore.isIgnoring(sender, target.getUniqueId())) {
            // You have ignored them
            plugin.send(sender, mini("<red>You are ignoring that player. Use /ignore <name> to remove."));
            return false;
        }

        // Disabled PM toggle (per target player)
        if (plugin.getPlayerSettingsService().isPmDisabled(target)
                && !sender.hasPermission("lpc.msg.bypass")) {
            plugin.send(sender, mini(disabledPmMessage));
            return false;
        }

        // Fire cancellable event
        LPCPrivateMessageEvent event = new LPCPrivateMessageEvent(true, sender, target, rawMessage);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            if (event.getCancelReason() != null) plugin.send(sender, mini(event.getCancelReason()));
            return false;
        }

        // Render message safely
        boolean allowColor = sender.hasPermission("lpc.chatcolor");
        ChatFormatService cfs = plugin.getChatFormatService();
        Component safeMessage = cfs.messageComponent(rawMessage, allowColor);
        safeMessage = plugin.getEmojiReplacer().apply(sender, safeMessage);
        safeMessage = plugin.getUrlLinkifier().apply(sender, safeMessage, plugin.isPaper());
        // Glyphs (Nexo)
        safeMessage = plugin.getGlyphService().applyForPlayer(sender, safeMessage);

        Component senderView = mini(safeMsgFormat(senderFormat),
                Placeholder.unparsed("sender", sender.getName()),
                Placeholder.unparsed("receiver", target.getName()),
                Placeholder.component("message", safeMessage));
        Component receiverView = mini(safeMsgFormat(receiverFormat),
                Placeholder.unparsed("sender", sender.getName()),
                Placeholder.unparsed("receiver", target.getName()),
                Placeholder.component("message", safeMessage));

        plugin.send(sender, senderView);
        plugin.send(target, receiverView);

        // Track reply chains
        replyTarget.put(target.getUniqueId(), sender.getUniqueId()); // now target can /r to sender
        replyTarget.put(sender.getUniqueId(), target.getUniqueId()); // sender can /r back
        lastSentTo.put(sender.getUniqueId(), target.getUniqueId());

        // Notifications for receiver (if not ignored)
        plugin.getNotificationService().notify(target, "private-message",
                Map.of("sender", sender.getName()));

        // Social spy broadcast
        spyBroadcast(sender, target, rawMessage);

        // Statistics
        plugin.getStatisticsService().incrementDmSent(sender);
        plugin.getStatisticsService().incrementDmReceived(target);

        // Discord relay (if enabled)
        DiscordService ds = plugin.getDiscordService();
        if (ds.isEnabled()) {
            PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
            ds.broadcastPrivateMessage(sender.getName(), target.getName(), plain.serialize(safeMessage));
        }

        return true;
    }

    /** Performs /reply using the last incoming partner. */
    public boolean reply(Player sender, String rawMessage) {
        if (!enabled) return false;
        if (!sender.hasPermission("lpc.reply") && !sender.hasPermission("lpc.msg")) {
            plugin.send(sender, mini("<red>You don't have permission to reply."));
            return false;
        }
        UUID targetId = replyTarget.get(sender.getUniqueId());
        if (targetId == null) {
            plugin.send(sender, mini(noReplyMessage));
            return false;
        }
        Player target = plugin.getServer().getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            replyTarget.remove(sender.getUniqueId());
            plugin.send(sender, mini(noReplyMessage));
            return false;
        }
        return sendMessage(sender, target, rawMessage);
    }

    /** Broadcast the private message to social-spy viewers. */
    private void spyBroadcast(Player sender, Player receiver, String raw) {
        if (!socialSpyEnabled) return;
        Component spyLine = mini(socialSpyFormat,
                Placeholder.unparsed("sender", sender.getName()),
                Placeholder.unparsed("receiver", receiver.getName()),
                Placeholder.unparsed("message", raw));
        VanishService vanish = plugin.getVanishService();
        IgnoreService ignore = plugin.getIgnoreService();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.equals(sender) || p.equals(receiver)) continue;
            if (!p.hasPermission("lpc.socialspy")) continue;
            if (sender.hasPermission("lpc.socialspy.bypass")) continue;
            if (vanish.isVanished(sender) && !p.hasPermission("lpc.vanish.see")) continue;
            if (ignore.isEnabled() && ignore.isIgnoring(p, sender.getUniqueId())) continue;
            plugin.send(p, spyLine);
        }
    }

    private Component mini(String text) { return miniMessage.deserialize(text); }

    private Component mini(String text, TagResolver... resolvers) {
        return miniMessage.deserialize(text, resolvers);
    }

    private static TagResolver unparsed(String key, String value) {
        return Placeholder.unparsed(key, value);
    }

    private String safeMsgFormat(String fmt) {
        return fmt
                .replace("{sender}", "<sender>")
                .replace("{receiver}", "<receiver>")
                .replace("{message}", "<message>");
    }
}
