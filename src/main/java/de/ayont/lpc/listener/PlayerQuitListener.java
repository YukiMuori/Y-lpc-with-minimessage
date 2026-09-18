package de.ayont.lpc.listener;

import de.ayont.lpc.LPC;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerQuitListener implements Listener {

    private final LPC plugin;

    public PlayerQuitListener(LPC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerSettingsService().onQuit(event.getPlayer().getUniqueId());
    }
}
