package de.ayont.lpc.commands;

import de.ayont.lpc.LPC;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Private-message commands (/msg, /w, /tell, /reply, /r). Each is a thin delegate to
 * {@link de.ayont.lpc.services.PrivateMessageService}.
 */
public final class MessageCommands implements CommandExecutor, TabCompleter {

    private final LPC plugin;
    private final boolean reply; // true = /r or /reply
    private final MiniMessage mm = MiniMessage.miniMessage();

    private MessageCommands(LPC plugin, boolean reply) {
        this.plugin = plugin;
        this.reply = reply;
    }

    public static void register(LPC plugin) {
        registerOne(plugin, "msg", false);
        registerOne(plugin, "w", false);
        registerOne(plugin, "tell", false);
        registerOne(plugin, "reply", true);
        registerOne(plugin, "r", true);
    }

    private static void registerOne(LPC plugin, String name, boolean reply) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) {
            // Plugin commands are declared in plugin.yml; if missing, skip.
            return;
        }
        MessageCommands exec = new MessageCommands(plugin, reply);
        cmd.setExecutor(exec);
        cmd.setTabCompleter(exec);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use private messages.");
            return true;
        }
        if (reply) {
            if (args.length == 0) {
                plugin.send(player, mm.deserialize("<red>Usage: /r <message>"));
                return true;
            }
            String msg = String.join(" ", args);
            plugin.getPrivateMessageService().reply(player, msg);
            return true;
        }
        if (args.length < 2) {
            plugin.send(player, mm.deserialize("<red>Usage: /" + label + " <player> <message>"));
            return true;
        }
        String targetName = args[0];
        String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            plugin.send(player, mm.deserialize("<red>Player not online."));
            return true;
        }
        if (target.equals(player)) {
            plugin.send(player, mm.deserialize("<red>You cannot message yourself."));
            return true;
        }
        plugin.getPrivateMessageService().sendMessage(player, target, msg);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                       @NotNull String[] args) {
        if (reply) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
