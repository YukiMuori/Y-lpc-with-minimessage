package de.ayont.lpc.listener;

import de.ayont.lpc.LPC;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Clears per-player transient state when a player disconnects. */
public class PlayerQuitListener implements Listener {

    private final LPC plugin;

    public PlayerQuitListener(LPC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerSettingsService().onQuit(event.getPlayer().getUniqueId());
        plugin.getBossBarService().clear(event.getPlayer());
        plugin.getInboxService().onQuit(event.getPlayer().getUniqueId());
    }
}
