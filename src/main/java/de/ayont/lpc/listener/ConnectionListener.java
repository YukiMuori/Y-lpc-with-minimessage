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
 * templates. When {@code join-messages.per-group} is enabled, per-group/track overrides from
 * {@link de.ayont.lpc.services.JoinLeaveService} take precedence and a join sound may play to the
 * joining player.
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
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore()
                && plugin.getConfig().getBoolean("join-messages.first-join.enabled", false);

        Component message;
        if (plugin.getJoinLeaveService().isEnabled()) {
            message = plugin.getJoinLeaveService().renderJoin(player, firstJoin);
        } else {
            if (!plugin.getConfig().getBoolean("join-messages.enabled", false)) {
                message = null;
            } else {
                String template = firstJoin
                        ? plugin.getConfig().getString("join-messages.first-join.format", "")
                        : plugin.getConfig().getString("join-messages.format", "");
                message = renderOrNull(player, template);
            }
        }

        if (plugin.isPaper()) {
            event.joinMessage(message);
        } else {
            event.setJoinMessage(legacyOrNull(message));
        }

        // Play join sound (to the joining player)
        if (plugin.getJoinLeaveService().isEnabled()) {
            plugin.getJoinLeaveService().playJoinSound(player);
        } else {
            String sound = plugin.getConfig().getString("join-messages.sound");
            if (sound != null && !sound.isEmpty()) {
                float vol = (float) plugin.getConfig().getDouble("join-messages.sound-volume", 1.0);
                float pitch = (float) plugin.getConfig().getDouble("join-messages.sound-pitch", 1.0);
                try { player.playSound(player.getLocation(), sound, vol, pitch); } catch (Exception ignored) {}
            }
        }

        relayDiscord(message, firstJoin ? "→ " + player.getName() + " joined for the first time!"
                : "→ " + player.getName() + " joined");

        // Inbox reminder — 1s delay so join messages settle
        plugin.getScheduler().runDelayed(() -> {
            if (player.isOnline()) plugin.getInboxService().onJoin(player);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Component message;
        if (plugin.getJoinLeaveService().isEnabled()) {
            message = plugin.getJoinLeaveService().renderQuit(player);
        } else {
            if (!plugin.getConfig().getBoolean("quit-messages.enabled", false)) {
                message = null;
            } else {
                message = renderOrNull(player, plugin.getConfig().getString("quit-messages.format", ""));
            }
        }
        if (plugin.isPaper()) {
            event.quitMessage(message);
        } else {
            event.setQuitMessage(legacyOrNull(message));
        }
        relayDiscord(message, "← " + player.getName() + " left");
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("death-messages.enabled", false)) return;
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
        if (template == null || template.isEmpty()) return null;
        return service.renderTemplate(player, template, plugin.displayNameOf(player));
    }

    private static String legacyOrNull(Component message) {
        return message == null ? null : LPC.getLegacySerializer().serialize(message);
    }
}
