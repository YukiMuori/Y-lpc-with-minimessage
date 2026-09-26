package de.ayont.lpc.commands;

import de.ayont.lpc.LPC;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Standalone {@code /inbox} (and aliases /messaggi, /mail) so players don't have to go
 * through {@code /lpc inbox}.
 */
public class InboxCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final LPC plugin;

    public InboxCommand(LPC plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command c, @NotNull String l,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can check their inbox.");
            return true;
        }
        if (!player.hasPermission("lpc.inbox")) {
            plugin.send(player, MM.deserialize("<red>Non hai il permesso."));
            return true;
        }
        var inbox = plugin.getInboxService();
        var msgs = inbox.drain(player);
        if (msgs.isEmpty()) {
            plugin.send(player, MM.deserialize(inbox.getEmptyMessage()));
            return true;
        }
        String header = inbox.getHeader()
                .replace("<count>", Integer.toString(msgs.size()))
                .replace("{count}", Integer.toString(msgs.size()));
        plugin.send(player, MM.deserialize(header));
        for (var m : msgs) {
            long ago = (System.currentTimeMillis() - m.receivedAtEpochMs()) / 1000L;
            String agoStr = ago < 60 ? ago + "s fa" : (ago < 3600 ? (ago/60) + "m fa" : (ago/3600) + "h fa");
            String line = inbox.getLineFormat()
                    .replace("<from>", m.from()).replace("{from}", m.from())
                    .replace("<ago>", agoStr).replace("{ago}", agoStr)
                    .replace("<msg>", esc(m.preview())).replace("{msg}", esc(m.preview()));
            plugin.send(player, MM.deserialize(line));
        }
        return true;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "\\<");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String a,
                                       @NotNull String[] args) {
        return List.of();
    }
}
