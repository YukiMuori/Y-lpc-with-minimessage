package de.ayont.lpc.chatgames;

import de.ayont.lpc.LPC;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** %lpc_chatgames_wins%. Only loaded when PlaceholderAPI is installed. */
public final class ChatGamesExpansion extends PlaceholderExpansion {

    private final LPC plugin;

    public ChatGamesExpansion(LPC plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "lpc"; }
    @Override public String getAuthor() { return "YukiMuori"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params.equalsIgnoreCase("chatgames_wins")) {
            return player == null ? "0" : Integer.toString(plugin.getChatGamesService().wins(player.getUniqueId()));
        }
        return null;
    }
}
