package de.ayont.lpc.commands;

import de.ayont.lpc.LPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class IgnoreCommand implements CommandExecutor, TabCompleter {

    private final LPC plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private IgnoreCommand(LPC plugin) { this.plugin = plugin; }

    public static void register(LPC plugin) {
        PluginCommand cmd = plugin.getCommand("ignore");
        if (cmd == null) return;
        IgnoreCommand exec = new IgnoreCommand(plugin);
        cmd.setExecutor(exec);
        cmd.setTabCompleter(exec);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /ignore.");
            return true;
        }
        if (!player.hasPermission("lpc.ignore")) {
            plugin.send(player, mm.deserialize("<red>You don't have permission to use ignore."));
            return true;
        }
        var ignore = plugin.getIgnoreService();
        if (!ignore.isEnabled()) {
            plugin.send(player, mm.deserialize("<red>Ignore is disabled."));
            return true;
        }
        if (args.length == 0) {
            showList(player);
            return true;
        }
        String targetName = args[0];
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            // Try exact online match
            Player online = plugin.getServer().getPlayerExact(targetName);
            if (online == null) {
                plugin.send(player, mm.deserialize("<red>Player not found."));
                return true;
            }
            target = online;
        }
        UUID targetId = target.getUniqueId();
        // Refuse to ignore players with bypass permission
        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null && ignore.hasBypass(onlineTarget)) {
                plugin.send(player, mm.deserialize("<red>You cannot ignore that player."));
                return true;
            }
        }
        boolean nowIgnored = ignore.toggle(player, targetId, target.getName() == null ? targetName : target.getName());
        if (nowIgnored) {
            plugin.send(player, mm.deserialize("<gray>Now ignoring <white><name></white>.",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("name", targetName)));
        } else {
            plugin.send(player, mm.deserialize("<gray>No longer ignoring <white><name></white>.",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("name", targetName)));
        }
        return true;
    }

    private void showList(Player player) {
        Set<UUID> ignored = plugin.getIgnoreService().getIgnored(player);
        plugin.send(player, mm.deserialize("<gradient:#FED83D:#BE2086>Ignore list</gradient> <gray>(" + ignored.size() + ")"));
        if (ignored.isEmpty()) {
            plugin.send(player, mm.deserialize("<gray>You are not ignoring anyone."));
            return;
        }
        for (UUID id : ignored) {
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op.getName() != null ? op.getName() : id.toString();
            plugin.send(player, Component.text(" - " + name));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                       @NotNull String[] args) {
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
