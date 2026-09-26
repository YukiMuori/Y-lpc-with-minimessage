package de.ayont.lpc.commands;

import de.ayont.lpc.LPC;
import de.ayont.lpc.services.AnnouncementService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Manual announcement command: {@code /announce <chat|actionbar|bossbar|title> <message>}.
 * Lets admins broadcast a one-shot message through any of the announcement channels
 * without scheduling it in announcements.yml.
 */
public class AnnounceCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> CHANNELS = List.of("chat", "actionbar", "bossbar", "title");
    private static final List<String> COLORS = List.of("pink", "blue", "red", "green", "yellow", "purple", "white");

    public static void register(LPC plugin) {
        var cmd = plugin.getCommand("announce");
        var ex = new AnnounceCommand(plugin);
        if (cmd != null) {
            cmd.setExecutor(ex);
            cmd.setTabCompleter(ex);
        }
    }

    private final LPC plugin;

    public AnnounceCommand(LPC plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command c, @NotNull String l,
                             @NotNull String[] args) {
        if (!sender.hasPermission("lpc.announcements.send")) {
            plugin.send(sender, MM.deserialize("<red>Non hai il permesso."));
            return true;
        }
        if (args.length < 2) {
            plugin.send(sender, MM.deserialize(
                    "<red>Uso: <white>/announce <chat|actionbar|bossbar|title> <messaggio>"));
            return true;
        }
        String channel = args[0].toLowerCase();
        if (!CHANNELS.contains(channel)) {
            plugin.send(sender, MM.deserialize(
                    "<red>Canale non valido. Usa: <white>chat, actionbar, bossbar, title"));
            return true;
        }
        String msg = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Component text = MM.deserialize(msg);
        String prefix = MM.serialize(MM.deserialize(
                plugin.getConfig().getString("announcements.manual-prefix",
                        "<dark_gray>[<gold>Annuncio</gold>]</dark_gray> ")));
        Component line = "chat".equals(channel) ? MM.deserialize(prefix).append(text) : text;

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            deliver(p, channel, line, text);
        }
        plugin.send(sender, MM.deserialize("<green>Annuncio inviato sul canale <white>" + channel + "</white>."));
        return true;
    }

    private void deliver(Player p, String channel, Component line, Component raw) {
        switch (channel) {
            case "chat" -> plugin.send(p, line);
            case "actionbar" -> {
                if (plugin.isPaper()) p.sendActionBar(raw);
                else plugin.send(p, raw);
            }
            case "bossbar" -> {
                if (plugin.isPaper()) {
                    String color = plugin.getConfig().getString("announcements.manual-bossbar.color", "yellow");
                    String overlay = plugin.getConfig().getString("announcements.manual-bossbar.overlay", "progress");
                    double seconds = plugin.getConfig().getDouble("announcements.manual-bossbar.seconds", 5);
                    BossBar bar = BossBar.bossBar(raw, 1f,
                            de.ayont.lpc.services.BossBarService.color(color),
                            de.ayont.lpc.services.BossBarService.overlay(overlay));
                    p.showBossBar(bar);
                    plugin.getScheduler().runDelayed(() -> {
                        if (p.isOnline()) p.hideBossBar(bar);
                    }, Math.max(20L, (long) (seconds * 20L)));
                } else plugin.send(p, raw);
            }
            case "title" -> {
                if (plugin.isPaper()) {
                    Title.Times t = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000));
                    p.showTitle(Title.title(raw, Component.empty(), t));
                } else plugin.send(p, raw);
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String a,
                                       @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return CHANNELS.stream().filter(x -> x.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
