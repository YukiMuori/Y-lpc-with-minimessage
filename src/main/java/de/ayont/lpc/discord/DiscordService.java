package de.ayont.lpc.discord;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Abstraction over a Discord bridge. The core LPC plugin calls these hooks after formatting
 * a message; an implementation (bundled JDA bot, an external plugin adapter, or a third-party
 * bridge) delivers them to Discord. Implementations may be a no-op when no bot is configured.
 * <p>
 * Security: the Minecraft → Discord payload is plain server-formatted text (components are
 * serialized with a safe plain-text serializer). The Discord → Minecraft path MUST sanitise
 * user input before handing it to the Minecraft chat: it must be treated as UNTRUSTED player
 * input (no MiniMessage parsing, no click events, ...).
 */
public interface DiscordService {

    /** @return true if Discord integration is enabled and a bot is connected. */
    boolean isEnabled();

    /** Broadcast a global Minecraft chat line to the configured Discord channel. */
    void broadcastChat(String senderName, String plainMessage);

    /** Broadcast a staff-chat line to the configured Discord staff channel. */
    void broadcastStaffChat(String senderName, String plainMessage);

    /** Broadcast a private message (Spy-only mode, if configured). */
    void broadcastPrivateMessage(String senderName, String receiverName, String plainMessage);

    /** Sends a join/quit/death event line to Discord when configured. */
    void broadcastEvent(String plainMessage);

    /** Shut down the service and release resources. */
    void shutdown();

    /** No-op implementation: Discord integration disabled. */
    static DiscordService disabled() {
        return new DiscordService() {
            @Override public boolean isEnabled() { return false; }
            @Override public void broadcastChat(String s, String m) {}
            @Override public void broadcastStaffChat(String s, String m) {}
            @Override public void broadcastPrivateMessage(String s, String r, String m) {}
            @Override public void broadcastEvent(String m) {}
            @Override public void shutdown() {}
        };
    }
}
