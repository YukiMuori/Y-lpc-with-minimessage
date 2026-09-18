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

import java.util.List;

/**
 * Quick shortcut commands: /staffchat (/sc), /clearchat (/cc), /socialspy.
 */
public final class QuickChatCommands implements CommandExecutor, TabCompleter {

    private final LPC plugin;
    private final String type;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private QuickChatCommands(LPC plugin, String type) {
        this.plugin = plugin;
        this.type = type;
    }

    public static void register(LPC plugin) {
        register(plugin, "staffchat", "staffchat");
        register(plugin, "sc", "staffchat");
        register(plugin, "clearchat", "clearchat");
        register(plugin, "cc", "clearchat");
        register(plugin, "socialspy", "socialspy");
    }

    private static void register(LPC plugin, String name, String type) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) return;
        QuickChatCommands exec = new QuickChatCommands(plugin, type);
        cmd.setExecutor(exec);
        cmd.setTabCompleter(exec);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        switch (type) {
            case "staffchat" -> handleStaffChat(player, args);
            case "clearchat" -> plugin.getClearChatService().clearChat(player);
            case "socialspy" -> handleSocialSpy(player);
        }
        return true;
    }

    private void handleStaffChat(Player player, String[] args) {
        if (!player.hasPermission("lpc.staffchat")) {
            plugin.send(player, mm.deserialize("<red>You don't have permission to use staff chat."));
            return;
        }
        if (args.length == 0) {
            // toggle mode
            boolean on = plugin.getPlayerSettingsService().toggleStaffChat(player);
            plugin.send(player, mm.deserialize(on
                    ? "<gradient:#FED83D:#BE2086>Staff chat</gradient> <green>enabled</green>. All messages will go to staff."
                    : "<gradient:#FED83D:#BE2086>Staff chat</gradient> <red>disabled</red>."));
            return;
        }
        String message = String.join(" ", args);
        plugin.getStaffChatService().sendMessage(player, message);
    }

    private void handleSocialSpy(Player player) {
        if (!player.hasPermission("lpc.socialspy")) {
            plugin.send(player, mm.deserialize("<red>You don't have permission."));
            return;
        }
        boolean on = plugin.getPlayerSettingsService().toggleSocialSpy(player);
        plugin.send(player, mm.deserialize(on
                ? "<dark_gray>[SocialSpy]</dark_gray> <green>enabled</green>."
                : "<dark_gray>[SocialSpy]</dark_gray> <red>disabled</red>."));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                       @NotNull String[] args) {
        return List.of();
    }
}
