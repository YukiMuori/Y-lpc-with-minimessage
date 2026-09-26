package de.ayont.lpc.commands;

import de.ayont.lpc.LPC;
import de.ayont.lpc.commands.AnnounceCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LPCCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "reload", "version", "help", "mute", "unmute",
            "slowmode", "clearchat", "cc",
            "notifications", "stats", "inbox", "announce", "announcements");
    private static final List<String> TARGET_SUBCOMMANDS = List.of("mute", "unmute");
    private static final List<String> SLOWMODE_ARGS = List.of("off");
    private static final List<String> ANNOUNCE_SUBS = List.of("reload", "list", "send", "help");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final LPC plugin;

    public LPCCommand(LPC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "version" -> handleVersion(sender);
            case "mute" -> handleMute(sender, args);
            case "unmute" -> handleUnmute(sender, args);
            case "slowmode" -> handleSlowMode(sender, args);
            case "clearchat", "cc" -> {
                if (sender instanceof Player p) plugin.getClearChatService().clearChat(p);
                else sender.sendMessage("Only players can clear chat.");
            }
            case "notifications" -> handleNotifications(sender, args);
            case "stats" -> handleStats(sender, args);
            case "inbox" -> handleInbox(sender);
            case "announcements" -> handleAnnouncements(sender, args);
            case "announce" -> handleAnnounceShortcut(sender, args);
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("lpc.reload")) {
            plugin.send(sender, mini("<red>You don't have permission to do that."));
            return;
        }
        plugin.reloadConfig();
        plugin.reloadServices();
        String raw = plugin.getConfig().getString("reload-message", "<green>Reloaded LPC configuration!");
        plugin.send(sender, mini(raw));
    }

    @SuppressWarnings("deprecation")
    private void handleVersion(CommandSender sender) {
        String platform = plugin.isFolia() ? "Folia" : plugin.isPaper() ? "Paper" : "Spigot";
        plugin.send(sender, mini("<gradient:#FED83D:#BE2086>LPC Chat Suite</gradient> <gray>v<white>"
                + plugin.getDescription().getVersion() + "</white> <dark_gray>— <gray>MiniMessage chat formatter."));
        java.util.Properties build = readBuildInfo();
        plugin.send(sender, mini("<dark_gray>Build: <gray>compiled for Minecraft <white>"
                + build.getProperty("minecraft", "?") + "</white> <dark_gray>(Java "
                + build.getProperty("java", "?") + " · Adventure " + build.getProperty("adventure", "?") + ")"));
        plugin.send(sender, mini("<dark_gray>Running on: <white>" + platform + " "
                + plugin.getServer().getBukkitVersion() + "</white> <dark_gray>(Java "
                + System.getProperty("java.version") + ")"));
    }

    private static java.util.Properties readBuildInfo() {
        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream in = LPCCommand.class.getResourceAsStream("/lpc-build.properties")) {
            if (in != null) props.load(in);
        } catch (Exception ignored) {
        }
        return props;
    }

    private void handleMute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lpc.mute")) {
            plugin.send(sender, mini("<red>You don't have permission to do that."));
            return;
        }
        if (!plugin.getMuteService().areCommandsEnabled()) {
            plugin.send(sender, mini("<red>Mute commands are disabled in the config."));
            return;
        }
        if (args.length < 2) {
            plugin.send(sender, mini("<red>Usage: /lpc mute <player> [duration e.g. 10m]"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            plugin.send(sender, mini("<red>Player <white><name></white> is not online.", "name", args[1]));
            return;
        }
        long duration = args.length >= 3 ? parseDuration(args[2]) : 0L;
        plugin.getMuteService().mute(target.getUniqueId(), System.currentTimeMillis(), duration);
        String suffix = duration > 0 ? " for " + args[2] : " permanently";
        plugin.send(sender, mini("<green>Muted <white><name></white>" + suffix + ".", "name", target.getName()));
    }

    private void handleUnmute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lpc.mute")) {
            plugin.send(sender, mini("<red>You don't have permission to do that."));
            return;
        }
        if (args.length < 2) {
            plugin.send(sender, mini("<red>Usage: /lpc unmute <player>"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            plugin.send(sender, mini("<red>Player <white><name></white> is not online.", "name", args[1]));
            return;
        }
        plugin.getMuteService().unmute(target.getUniqueId());
        plugin.send(sender, mini("<green>Unmuted <white><name></white>.", "name", target.getName()));
    }

    private void handleSlowMode(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lpc.slowmode")) {
            plugin.send(sender, mini("<red>You don't have permission to do that."));
            return;
        }
        if (args.length < 2) {
            plugin.send(sender, mini("<red>Usage: /lpc slowmode <seconds|off>"));
            return;
        }
        int seconds;
        if (args[1].equalsIgnoreCase("off")) {
            seconds = 0;
        } else {
            try {
                seconds = Math.max(0, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                plugin.send(sender, mini("<red>Invalid number: <white><arg></white>", "arg", args[1]));
                return;
            }
        }
        int newVal = plugin.getSlowModeService().setSlowMode(seconds);
        if (newVal == 0) {
            plugin.send(sender, mini("<green>Slow mode disabled."));
        } else {
            plugin.send(sender, mini("<green>Slow mode set to <white><seconds></white>s.",
                    "seconds", Integer.toString(newVal)));
        }
    }

    private void handleNotifications(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can toggle notifications.");
            return;
        }
        if (!player.hasPermission("lpc.notifications")) {
            plugin.send(player, mini("<red>You don't have permission."));
            return;
        }
        if (args.length < 2) {
            boolean on = plugin.getNotificationService().toggleAll(player);
            plugin.send(player, mini(on
                    ? "<green>All notifications enabled.</green>"
                    : "<red>All notifications disabled.</red>"));
            return;
        }
        String type = args[1].toLowerCase();
        boolean on = plugin.getNotificationService().toggle(player, type);
        plugin.send(player, mini(on
                ? "<green>Notifications for <white><type></white> enabled.</green>"
                : "<red>Notifications for <white><type></white> disabled.</red>",
                "type", type));
    }

    private void handleInbox(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can check their inbox.");
            return;
        }
        var inbox = plugin.getInboxService();
        var msgs = inbox.drain(player);
        if (msgs.isEmpty()) {
            plugin.send(player, MM.deserialize(inbox.getEmptyMessage()));
            return;
        }
        String header = inbox.getHeader().replace("<count>", Integer.toString(msgs.size()))
                .replace("{count}", Integer.toString(msgs.size()));
        plugin.send(player, MM.deserialize(header));
        for (var m : msgs) {
            long ago = (System.currentTimeMillis() - m.receivedAtEpochMs()) / 1000L;
            String agoStr = ago < 60 ? ago + "s fa" : (ago < 3600 ? (ago/60) + "m fa" : (ago/3600) + "h fa");
            String line = inbox.getLineFormat()
                    .replace("<from>", m.from()).replace("{from}", m.from())
                    .replace("<ago>", agoStr).replace("{ago}", agoStr)
                    .replace("<msg>", escapeMini(m.preview()))
                    .replace("{msg}", escapeMini(m.preview()));
            plugin.send(player, MM.deserialize(line));
        }
    }

    private static String escapeMini(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "\\<");
    }

    private void handleAnnouncements(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lpc.announcements")) {
            plugin.send(sender, mini("<red>Non hai il permesso."));
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase() : "help";
        switch (sub) {
            case "reload" -> {
                plugin.getAnnouncementService().reload();
                plugin.send(sender, mini("<green>Annunci ricaricati da announcements.yml."));
            }
            case "list" -> {
                plugin.send(sender, mini("<yellow>Annunci caricati: <white>" + plugin.getAnnouncementService().count() + "</white>."));
                plugin.send(sender, mini("<gray>Usa <white>/lpc announcements reload</white> per ricaricare,"));
                plugin.send(sender, mini("<gray>o <white>/announce <chat|actionbar|bossbar|title> <msg></white> per inviare un annuncio manuale."));
            }
            default -> {
                plugin.send(sender, mini("<gradient:#FED83D:#BE2086>Annunci</gradient>"));
                plugin.send(sender, mini("<dark_gray>- <white>/lpc announcements reload</white> <dark_gray>» <gray>Ricarica announcements.yml"));
                plugin.send(sender, mini("<dark_gray>- <white>/lpc announcements list</white> <dark_gray>» <gray>Mostra numero annunci"));
                plugin.send(sender, mini("<dark_gray>- <white>/announce <canale> <msg></white> <dark_gray>» <gray>Invia un annuncio ora"));
            }
        }
    }

    private void handleAnnounceShortcut(CommandSender sender, String[] args) {
        // Forward to /announce logic
        var cmd = new AnnounceCommand(plugin);
        // args[0] is "announce", shift
        String[] shifted = new String[Math.max(0, args.length - 1)];
        System.arraycopy(args, 1, shifted, 0, shifted.length);
        cmd.onCommand(sender, null, "announce", shifted);
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can view statistics.");
            return;
        }
        if (args.length < 2) {
            plugin.getStatisticsService().showOwnStats(player);
        } else {
            plugin.getStatisticsService().showOtherStats(player, args[1]);
        }
    }

    private void sendHelp(CommandSender sender) {
        plugin.send(sender, mini("<gradient:#FED83D:#BE2086>LPC Chat Suite</gradient> <gray>comandi:"));
        plugin.send(sender, mini("<dark_gray>- <white>/lpc reload</white> <dark_gray>» <gray>Ricarica configurazione"));
        plugin.send(sender, mini("<dark_gray>- <white>/lpc version</white> <dark_gray>» <gray>Versione plugin"));
        plugin.send(sender, mini("<dark_gray>- <white>/lpc slowmode <s|off></white> <dark_gray>» <gray>Imposta slow mode"));
        plugin.send(sender, mini("<dark_gray>- <white>/lpc clearchat</white> <dark_gray>» <gray>Pulisci chat"));
        plugin.send(sender, mini("<dark_gray>- <white>/lpc notifications [tipo]</white> <dark_gray>» <gray>Attiva/disattiva notifiche"));
        plugin.send(sender, mini("<dark_gray>- <white>/lpc stats [player]</white> <dark_gray>» <gray>Vedi statistiche"));
        plugin.send(sender, mini("<dark_gray>- <white>/inbox</white> <dark_gray>» <gray>Messaggi privati non letti"));
        plugin.send(sender, mini("<dark_gray>- <white>/msg, /w, /tell, /r</white> <dark_gray>» <gray>Messaggi privati"));
        plugin.send(sender, mini("<dark_gray>- <white>/ignore <player></white> <dark_gray>» <gray>Ignora un giocatore"));
        plugin.send(sender, mini("<dark_gray>- <white>/sc [msg]</white> <dark_gray>» <gray>Staff chat"));
        plugin.send(sender, mini("<dark_gray>- <white>/socialspy</white> <dark_gray>» <gray>Toggle social spy"));
        plugin.send(sender, mini("<dark_gray>- <white>/announce <canale> <msg></white> <dark_gray>» <gray>Invia un annuncio"));
        plugin.send(sender, mini("<dark_gray>- <white>/lpc announcements</white> <dark_gray>» <gray>Gestisci annunci"));
        if (plugin.getMuteService().areCommandsEnabled()) {
            plugin.send(sender, mini("<dark_gray>- <white>/lpc mute/unmute</white> <dark_gray>» <gray>Muta un giocatore"));
        }
    }

    static long parseDuration(String input) {
        if (input == null || input.isBlank()) return 0L;
        String trimmed = input.trim();
        char unit = trimmed.charAt(trimmed.length() - 1);
        try {
            if (Character.isDigit(unit)) return Long.parseLong(trimmed) * 1000L;
            long amount = Long.parseLong(trimmed.substring(0, trimmed.length() - 1).trim());
            return switch (Character.toLowerCase(unit)) {
                case 's' -> amount * 1000L;
                case 'm' -> amount * 60_000L;
                case 'h' -> amount * 3_600_000L;
                case 'd' -> amount * 86_400_000L;
                default -> 0L;
            };
        } catch (NumberFormatException invalid) {
            return 0L;
        }
    }

    private static Component mini(String raw) { return MM.deserialize(raw); }
    private static Component mini(String raw, String key, String value) {
        return MM.deserialize(raw, Placeholder.unparsed(key, value));
    }
    private static Component mini(String raw, String k1, String v1, String k2, String v2, String k3, String v3) {
        return MM.deserialize(raw,
                Placeholder.unparsed(k1, v1),
                Placeholder.unparsed(k2, v2),
                Placeholder.unparsed(k3, v3));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(sub -> sub.startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            String cmd0 = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            if (TARGET_SUBCOMMANDS.contains(cmd0) || cmd0.equals("stats")) {
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .toList();
            }
            if (cmd0.equals("slowmode")) {
                return SLOWMODE_ARGS.stream().filter(s -> s.startsWith(prefix)).toList();
            }
            if (cmd0.equals("notifications")) {
                List<String> types = new ArrayList<>(plugin.getNotificationService().getAvailableTypes());
                types.add("all");
                return types.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
            }
            if (cmd0.equals("announcements")) {
                return ANNOUNCE_SUBS.stream().filter(s -> s.startsWith(prefix)).toList();
            }
            if (cmd0.equals("announce")) {
                return List.of("chat","actionbar","bossbar","title").stream()
                        .filter(s -> s.startsWith(prefix)).toList();
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("announce")) {
            return List.of();
        }
        return List.of();
    }
}
