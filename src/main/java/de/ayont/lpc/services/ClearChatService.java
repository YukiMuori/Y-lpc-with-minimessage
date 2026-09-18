package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Visual chat-clearing: sends {@code lines} blank messages to each online player's chat
 * (optionally with a header/footer). Logs and console history are unaffected.
 */
public final class ClearChatService {

    private final LPC plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private volatile boolean enabled;
    private volatile int lines;
    private volatile String clearedMessage;

    public ClearChatService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("clear-chat.enabled", true);
        this.lines = c.getInt("clear-chat.lines", 100);
        this.clearedMessage = c.getString("clear-chat.message",
                "<gray>Chat was cleared by a staff member.");
    }

    public boolean isEnabled() { return enabled; }

    public void clearChat(Player staff) {
        if (!enabled) return;
        if (!staff.hasPermission("lpc.clearchat")) {
            plugin.send(staff, mm.deserialize("<red>You don't have permission to do that."));
            return;
        }
        Component blank = Component.text("");
        Component notice = mm.deserialize(clearedMessage);
        Component header = mm.deserialize("<gradient:#FED83D:#BE2086>LPC</gradient> <gray>— ");
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.hasPermission("lpc.clearchat.bypass")) continue;
            for (int i = 0; i < lines; i++) {
                p.sendMessage(blank);
            }
            p.sendMessage(header.append(notice));
        }
        plugin.getLogger().info("Chat cleared by " + staff.getName());
    }
}
