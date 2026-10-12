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
            sender.sendMessage("Solo in gioco.");
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
            plugin.send(player, mm.deserialize("<#FF5C7A>Non hai il permesso per la chat staff."));
            return;
        }
        if (args.length == 0) {
            // toggle mode
            boolean on = plugin.getPlayerSettingsService().toggleStaffChat(player);
            plugin.send(player, mm.deserialize(on
                    ? "<white><font:nexo:default>ꘀ</font></white> <dark_gray>»</dark_gray> <#F5A3AA>Chat staff <#7EE2A8>attivata<#9C9C9C>: ora scrivi solo allo staff."
                    : "<white><font:nexo:default>ꘀ</font></white> <dark_gray>»</dark_gray> <#F5A3AA>Chat staff <#FF5C7A>disattivata<#9C9C9C>."));
            return;
        }
        String message = String.join(" ", args);
        plugin.getStaffChatService().sendMessage(player, message);
    }

    private void handleSocialSpy(Player player) {
        if (!player.hasPermission("lpc.socialspy")) {
            plugin.send(player, mm.deserialize("<#FF5C7A>Non hai il permesso."));
            return;
        }
        boolean on = plugin.getPlayerSettingsService().toggleSocialSpy(player);
        plugin.send(player, mm.deserialize(on
                ? "<white><font:nexo:default>ꘋ</font></white> <dark_gray>»</dark_gray> <#EEEEEE>Spia messaggi <#7EE2A8>attivata<#9C9C9C>."
                : "<white><font:nexo:default>ꘋ</font></white> <dark_gray>»</dark_gray> <#EEEEEE>Spia messaggi <#FF5C7A>disattivata<#9C9C9C>."));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                       @NotNull String[] args) {
        return List.of();
    }
}
