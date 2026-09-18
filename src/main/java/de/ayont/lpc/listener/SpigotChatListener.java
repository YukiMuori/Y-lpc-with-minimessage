package de.ayont.lpc.listener;

import de.ayont.lpc.LPC;
import de.ayont.lpc.chat.ChatFormatService;
import de.ayont.lpc.chat.ItemPlaceholder;
import de.ayont.lpc.chat.MentionService;
import de.ayont.lpc.discord.DiscordService;
import de.ayont.lpc.events.LPCChatMessageEvent;
import de.ayont.lpc.hooks.VanishService;
import de.ayont.lpc.moderation.ModResult;
import de.ayont.lpc.services.IgnoreService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Legacy Spigot chat listener. Moderates and decorates the message, renders through the shared
 * {@link ChatFormatService}, and bakes the result into the chat format as a legacy string.
 */
public class SpigotChatListener implements Listener {

    private final LPC plugin;
    private final ChatFormatService service;

    public SpigotChatListener(LPC plugin) {
        this.plugin = plugin;
        this.service = plugin.getChatFormatService();
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.isDisabledWorld(player.getWorld().getName())) {
            return;
        }

        if (plugin.getPlayerSettingsService().isInStaffChat(player)) {
            String raw = event.getMessage();
            event.setCancelled(true);
            plugin.getStaffChatService().sendMessage(player, raw);
            return;
        }

        Component slowErr = plugin.getSlowModeService().check(player);
        if (slowErr != null) {
            event.setCancelled(true);
            plugin.send(player, slowErr);
            return;
        }

        ModResult moderation = plugin.getModerationService().process(player, event.getMessage());
        if (moderation.isBlocked()) {
            event.setCancelled(true);
            plugin.getStatisticsService().incrementBlocked(player);
            if (moderation.notice() != null) {
                plugin.send(player, moderation.notice());
            }
            return;
        }
        String effectiveRaw = moderation.action() == ModResult.Action.TRANSFORM ? moderation.text() : event.getMessage();

        LPCChatMessageEvent pubEvent = new LPCChatMessageEvent(true, player, effectiveRaw);
        plugin.getServer().getPluginManager().callEvent(pubEvent);
        if (pubEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }
        effectiveRaw = pubEvent.getRawMessage();

        plugin.maybeItemPlaceholderHint(player, effectiveRaw);

        boolean allowColor = player.hasPermission("lpc.chatcolor");
        Component base = service.messageComponent(effectiveRaw, allowColor);
        base = plugin.getEmojiReplacer().apply(player, base);
        base = plugin.getUrlLinkifier().apply(player, base, false);
        base = plugin.getGlyphService().applyForPlayer(player, base);
        base = plugin.getMentionExtensionService().processSpecialMentions(player, effectiveRaw, base);

        VanishService vanish = plugin.getVanishService();
        Set<String> visibleNames = new HashSet<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (vanish.isVanished(online) && !player.hasPermission("lpc.vanish.see")) continue;
            visibleNames.add(online.getName());
        }

        MentionService.Result mention = plugin.getMentionService()
                .highlight(base, visibleNames);

        IgnoreService ignore = plugin.getIgnoreService();
        Set<String> filteredMentions = new HashSet<>();
        for (String name : mention.mentioned()) {
            Player target = plugin.getServer().getPlayerExact(name);
            if (target == null) continue;
            if (ignore.isEnabled() && ignore.isIgnoring(target, player.getUniqueId()) && !ignore.hasBypass(player)) continue;
            if (vanish.isVanished(target) && !player.hasPermission("lpc.vanish.see")) continue;
            filteredMentions.add(name);
        }
        plugin.getMentionService().pingAll(filteredMentions, player.getName());
        for (String n : filteredMentions) {
            Player p = plugin.getServer().getPlayerExact(n);
            if (p != null) plugin.getStatisticsService().incrementMention(player, p, false, false);
        }

        Component displayName = plugin.displayNameOf(player);
        Component hoverName = plugin.getPlayerHoverService().isEnabled()
                ? plugin.getPlayerHoverService().apply(player, displayName) : displayName;

        Component rendered = service.render(player, mention.message(), hoverName);
        rendered = ItemPlaceholder.apply(plugin, player, rendered, false);

        plugin.getStatisticsService().incrementMessage(player);

        DiscordService discord = plugin.getDiscordService();
        if (discord.isEnabled()) {
            discord.broadcastChat(player.getName(), PlainTextComponentSerializer.plainText().serialize(base));
        }

        String legacy = LPC.getLegacySerializer().serialize(rendered).replace("%", "%%");
        event.setFormat(legacy);

        if (ignore.isEnabled() && ignore.blocksChat()) {
            event.getRecipients().removeIf(p -> ignore.isIgnoring(p, player.getUniqueId()) && !ignore.hasBypass(player));
        }
    }
}
