package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import de.ayont.lpc.chat.ChatFormatService;
import de.ayont.lpc.discord.DiscordService;
import de.ayont.lpc.events.LPCStaffChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Staff chat: an isolated channel only visible to players with {@code lpc.staffchat} (and
 * optionally {@code lpc.staffchat.spy}). Toggleable via {@code /sc}; also supports one-shot
 * {@code /sc <message>}.
 */
public final class StaffChatService {

    private final LPC plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private volatile boolean enabled;
    private volatile String format;

    public StaffChatService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("staff-chat.enabled", true);
        this.format = c.getString("staff-chat.format",
                "<gradient:#FED83D:#BE2086>STAFF</gradient> <gray>{name} <dark_gray>»</dark_gray> <white>{message}</white>");
    }

    public boolean isEnabled() { return enabled; }

    /** Send a staff-chat message from the player (does NOT require staff-chat mode). */
    public void sendMessage(Player sender, String rawMessage) {
        if (!enabled) return;
        if (!sender.hasPermission("lpc.staffchat")) {
            plugin.send(sender, mm.deserialize("<red>You don't have permission to use staff chat."));
            return;
        }

        LPCStaffChatEvent event = new LPCStaffChatEvent(true, sender, rawMessage);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        boolean allowColor = sender.hasPermission("lpc.chatcolor");
        ChatFormatService cfs = plugin.getChatFormatService();
        Component safe = cfs.messageComponent(rawMessage, allowColor);
        safe = plugin.getEmojiReplacer().apply(sender, safe);
        safe = plugin.getUrlLinkifier().apply(sender, safe, plugin.isPaper());
        safe = plugin.getGlyphService().applyForPlayer(sender, safe);

        String senderName = sender.getName();
        Component rendered = mm.deserialize(
                format
                        .replace("{name}", "<name>")
                        .replace("{message}", "<message>"),
                Placeholder.unparsed("name", senderName),
                Placeholder.component("message", safe));

        // Send to all online staff (and spies)
        Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
        for (Player p : online) {
            boolean inStaff = plugin.getPlayerSettingsService().isInStaffChat(p);
            boolean perm = p.hasPermission("lpc.staffchat");
            boolean spy = p.hasPermission("lpc.staffchat.spy");
            if (perm || spy || inStaff) {
                plugin.send(p, rendered);
            }
        }

        // Notification
        for (Player p : online) {
            if (p.equals(sender)) continue;
            boolean canSee = p.hasPermission("lpc.staffchat") || p.hasPermission("lpc.staffchat.spy");
            if (canSee) {
                plugin.getNotificationService().notify(p, "staff-chat",
                        java.util.Map.of("sender", senderName));
            }
        }

        // Statistics + Discord relay
        plugin.getStatisticsService().incrementStaffMessage(sender);
        DiscordService ds = plugin.getDiscordService();
        if (ds.isEnabled()) {
            ds.broadcastStaffChat(senderName, PlainTextComponentSerializer.plainText().serialize(safe));
        }
    }
}
