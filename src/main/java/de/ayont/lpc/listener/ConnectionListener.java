package de.ayont.lpc.listener;

import de.ayont.lpc.LPC;
import de.ayont.lpc.chat.ChatFormatService;
import de.ayont.lpc.discord.DiscordService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Replaces the vanilla join / quit / first-join / death messages with operator-authored MiniMessage
 * templates. These events fire on the main thread, so config is read directly. No player chat text
 * is involved; the death cause is injected as a pre-built component via a placeholder.
 */
public class ConnectionListener implements Listener {

    private final LPC plugin;
    private final ChatFormatService service;

    public ConnectionListener(LPC plugin) {
        this.plugin = plugin;
        this.service = plugin.getChatFormatService();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("join-messages.enabled", false)) {
            return;
        }
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore()
                && plugin.getConfig().getBoolean("join-messages.first-join.enabled", false);
        String template = firstJoin
                ? plugin.getConfig().getString("join-messages.first-join.format", "")
                : plugin.getConfig().getString("join-messages.format", "");

        Component message = renderOrNull(player, template);
        if (plugin.isPaper()) {
            event.joinMessage(message);
        } else {
            event.setJoinMessage(legacyOrNull(message));
        }
        relayDiscord(message, firstJoin ? "→ " + player.getName() + " joined for the first time!"
                : "→ " + player.getName() + " joined");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("quit-messages.enabled", false)) {
            return;
        }
        Player player = event.getPlayer();
        Component message = renderOrNull(player, plugin.getConfig().getString("quit-messages.format", ""));
        if (plugin.isPaper()) {
            event.quitMessage(message);
        } else {
            event.setQuitMessage(legacyOrNull(message));
        }
        relayDiscord(message, "← " + player.getName() + " left");
    }

    @SuppressWarnings("deprecation") // getDeathMessage()/setDeathMessage are the Spigot fallback
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("death-messages.enabled", false)) {
            return;
        }
        Player player = event.getEntity();
        String template = plugin.getConfig().getString("death-messages.format", "");
        boolean showVanilla = plugin.getConfig().getBoolean("death-messages.show-vanilla-cause", true);

        Component cause = Component.empty();
        if (showVanilla) {
            if (plugin.isPaper()) {
                Component vanilla = event.deathMessage();
                cause = vanilla != null ? vanilla : Component.empty();
            } else {
                String legacy = event.getDeathMessage();
                cause = legacy != null ? LPC.getLegacySerializer().deserialize(legacy) : Component.empty();
            }
        }

        Component message = template == null || template.isEmpty()
                ? null
                : service.renderTemplate(player, template, plugin.displayNameOf(player),
                        Placeholder.component("death-message", cause));

        if (plugin.isPaper()) {
            event.deathMessage(message);
        } else {
            event.setDeathMessage(legacyOrNull(message));
        }
        relayDiscord(message, "☠ " + (message != null
                ? PlainTextComponentSerializer.plainText().serialize(message)
                : player.getName() + " died"));
    }

    private void relayDiscord(Component minecraftMessage, String fallbackPlain) {
        DiscordService ds = plugin.getDiscordService();
        if (!ds.isEnabled()) return;
        String plain = minecraftMessage != null
                ? PlainTextComponentSerializer.plainText().serialize(minecraftMessage)
                : fallbackPlain;
        ds.broadcastEvent(plain);
    }

    private Component renderOrNull(Player player, String template) {
        if (template == null || template.isEmpty()) {
            return null; // suppress the message
        }
        return service.renderTemplate(player, template, plugin.displayNameOf(player));
    }

    private static String legacyOrNull(Component message) {
        return message == null ? null : LPC.getLegacySerializer().serialize(message);
    }
}
