package de.ayont.lpc.discord;

import de.ayont.lpc.LPC;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Level;

/**
 * Default implementation used when no external bot plugin provides a {@link DiscordService}.
 * Reads {@code discord.enabled} from the config and stays in a disabled state unless an external
 * provider registers an implementation via {@link LPC#setDiscordService(DiscordService)}.
 * <p>
 * LPC itself does NOT shade JDA or any other Discord client library — ship your own bot plugin
 * (or register an implementation from another plugin) to enable full Discord bridging.
 */
public final class ConfiguredDiscordService implements DiscordService {

    private final LPC plugin;
    private boolean enabledFromConfig;

    public ConfiguredDiscordService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabledFromConfig = c.getBoolean("discord.enabled", false);
        if (enabledFromConfig) {
            plugin.getLogger().info("discord.enabled = true but no DiscordService implementation is registered. " +
                    "Install a companion bot plugin to enable Discord bridging, or set discord.enabled=false.");
        }
    }

    @Override public boolean isEnabled() { return false; } // we ourselves never send
    @Override public void broadcastChat(String s, String m) {}
    @Override public void broadcastStaffChat(String s, String m) {}
    @Override public void broadcastPrivateMessage(String s, String r, String m) {}
    @Override public void broadcastEvent(String m) {}
    @Override public void shutdown() {}
}
