package de.ayont.lpc.chatgames;

import de.ayont.lpc.LPC;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** /chatgames [start [tipo]|stop|reload|toggle|top] */
public final class ChatGamesCommand implements TabExecutor {

    private static final String ADMIN = "lpc.chatgames.admin";
    private final LPC plugin;

    public ChatGamesCommand(LPC plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ChatGamesService games = plugin.getChatGamesService();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start" -> {
                if (!admin(sender, games)) return true;
                String type = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : null;
                if (type != null && !ChatGamesService.TYPES.contains(type)) {
                    games.sendTo(sender, games.msg("unknown-type"), Placeholder.unparsed("types", String.join(", ", ChatGamesService.TYPES)));
                } else if (!games.start(type)) {
                    games.sendTo(sender, games.msg("already-running"));
                }
            }
            case "stop" -> {
                if (admin(sender, games) && !games.stop()) games.sendTo(sender, games.msg("no-game"));
            }
            case "reload" -> {
                if (!admin(sender, games)) return true;
                plugin.reloadServices();
                games.sendTo(sender, games.msg("reloaded"));
            }
            case "toggle" -> {
                if (sender instanceof Player p) games.sendTo(p, games.msg(games.toggle(p) ? "toggle-on" : "toggle-off"));
            }
            case "top" -> {
                var top = games.top(10);
                if (top.isEmpty()) { games.sendTo(sender, games.msg("top-empty")); return true; }
                games.sendTo(sender, games.msg("top-header"));
                for (int i = 0; i < top.size(); i++) {
                    games.sendTo(sender, games.msg("top-line"),
                            Placeholder.unparsed("pos", Integer.toString(i + 1)),
                            Placeholder.unparsed("player", top.get(i).name()),
                            Placeholder.unparsed("wins", ChatGameLogic.formatNumber(top.get(i).wins())));
                }
            }
            default -> {
                int wins = sender instanceof Player p ? games.wins(p.getUniqueId()) : 0;
                games.sendTo(sender, games.msg("help"), Placeholder.unparsed("wins", ChatGameLogic.formatNumber(wins)));
            }
        }
        return true;
    }

    private static boolean admin(CommandSender sender, ChatGamesService games) {
        if (sender.hasPermission(ADMIN)) return true;
        games.sendTo(sender, games.msg("no-permission"));
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            return Stream.of("toggle", "top", "start", "stop", "reload")
                    .filter(s -> admin || s.equals("toggle") || s.equals("top"))
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && admin && args[0].equalsIgnoreCase("start")) {
            return ChatGamesService.TYPES.stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
