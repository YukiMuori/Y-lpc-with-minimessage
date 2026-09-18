package de.ayont.lpc.hooks;

import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Thin wrapper around PlaceholderAPI's static API so services do not have to catch
 * {@link NoClassDefFoundError} whenever PlaceholderAPI is absent. The hook is only
 * instantiated when PlaceholderAPI is installed and enabled; {@link #isAvailable()}
 * is the preferred check.
 */
public final class PlaceholderAPIHook {

    private final boolean available;
    private final JavaPlugin plugin;

    private PlaceholderAPIHook(JavaPlugin plugin, boolean available) {
        this.plugin = plugin;
        this.available = available;
    }

    public static PlaceholderAPIHook create(JavaPlugin plugin) {
        PluginManager pm = plugin.getServer().getPluginManager();
        boolean papi = pm.getPlugin("PlaceholderAPI") != null
                && pm.getPlugin("PlaceholderAPI").isEnabled();
        return new PlaceholderAPIHook(plugin, papi);
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Applies PlaceholderAPI replacements to the text for the given player.
     * Returns the input unchanged when PAPI is absent.
     */
    public String setPlaceholders(Player player, String text) {
        if (!available || text == null) return text;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            plugin.getLogger().warning("PlaceholderAPI setPlaceholders failed: " + t.getMessage());
            return text;
        }
    }
}
