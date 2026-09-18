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
import de.ayont.lpc.services.PlayerHoverService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Set;

/**
 * Paper chat listener. Moderates the raw message, decorates the safe message component (emoji, URLs,
 * glyphs, mention highlighting, @staff/@everyone), then installs a per-viewer
 * {@link io.papermc.paper.chat.ChatRenderer} that applies per-player ignore filtering and
 * player-hover click/hover.
 */
public class AsyncChatListener implements Listener {

    private final LPC plugin;
    private final ChatFormatService service;

    public AsyncChatListener(LPC plugin) {
        this.plugin = plugin;
        this.service = plugin.getChatFormatService();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.isDisabledWorld(player.getWorld().getName())) {
            return;
        }

        // Staff-chat mode: intercept and route the message to staff channel
        if (plugin.getPlayerSettingsService().isInStaffChat(player)) {
            String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
            event.setCancelled(true);
            plugin.getStaffChatService().sendMessage(player, raw);
            return;
        }

        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Slow mode
        Component slowErr = plugin.getSlowModeService().check(player);
        if (slowErr != null) {
            event.setCancelled(true);
            plugin.send(player, slowErr);
            return;
        }

        ModResult moderation = plugin.getModerationService().process(player, raw);
        if (moderation.isBlocked()) {
            event.setCancelled(true);
            plugin.getStatisticsService().incrementBlocked(player);
            if (moderation.notice() != null) {
                plugin.send(player, moderation.notice());
            }
            return;
        }
        String effectiveRaw = moderation.action() == ModResult.Action.TRANSFORM ? moderation.text() : raw;

        // Fire cancellable public event BEFORE decorating.
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
        base = plugin.getUrlLinkifier().apply(player, base, true);
        base = plugin.getGlyphService().applyForPlayer(player, base);

        // @staff / @everyone — decorate component + notify
        base = plugin.getMentionExtensionService().processSpecialMentions(player, effectiveRaw, base);

        // Build set of online players, excluding vanished players the sender can't see
        VanishService vanish = plugin.getVanishService();
        Set<String> visibleNames = new HashSet<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (vanish.isVanished(online) && !player.hasPermission("lpc.vanish.see")) continue;
            visibleNames.add(online.getName());
        }

        MentionService.Result mention = plugin.getMentionService()
                .highlight(base, visibleNames);

        // Filter out ignored players from the mention set (avoid pinging them)
        IgnoreService ignore = plugin.getIgnoreService();
        Set<String> filteredMentions = new HashSet<>();
        for (String name : mention.mentioned()) {
            Player target = plugin.getServer().getPlayerExact(name);
            if (target == null) continue;
            if (ignore.isEnabled() && ignore.isIgnoring(target, player.getUniqueId()) && !ignore.hasBypass(player)) {
                continue; // don't ping someone who ignored the sender
            }
            if (vanish.isVanished(target) && !player.hasPermission("lpc.vanish.see")) continue;
            filteredMentions.add(name);
        }
        plugin.getMentionService().pingAll(filteredMentions, player.getName());
        for (String n : filteredMentions) {
            Player p = plugin.getServer().getPlayerExact(n);
            if (p != null) {
                plugin.getStatisticsService().incrementMention(player, p, false, false);
            }
        }

        Component finalMessage = mention.message();
        Component displayName = plugin.displayNameOf(player);

        // Apply hover to the player display-name for chat rendering (click-to-message + tooltip)
        PlayerHoverService hover = plugin.getPlayerHoverService();
        Component hoverName = hover.isEnabled() ? hover.apply(player, displayName) : displayName;

        // Stats
        plugin.getStatisticsService().incrementMessage(player);

        // Discord relay
        DiscordService discord = plugin.getDiscordService();
        if (discord.isEnabled()) {
            // Render the final line for the per-viewer; but for Discord use plain-text message only
            discord.broadcastChat(player.getName(),
                    PlainTextComponentSerializer.plainText().serialize(finalMessage));
        }

        event.renderer((source, sourceDisplayName, message, viewer) -> {
            // If the viewer is a player and has ignored the source, hide the message.
            if (viewer instanceof Player pViewer) {
                if (ignore.isEnabled() && ignore.blocksChat()
                        && ignore.isIgnoring(pViewer, source.getUniqueId())
                        && !ignore.hasBypass(source)) {
                    return Component.empty();
                }
            }
            Component line = service.render(source, finalMessage, hoverName);
            line = ItemPlaceholder.apply(plugin, source, line, true);
            return line;
        });
    }
}
