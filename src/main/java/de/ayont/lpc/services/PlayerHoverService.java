package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import de.ayont.lpc.hooks.PlaceholderAPIHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Builds the clickable player-name component with hover tooltip used in chat formats.
 * <p>
 * Hover lines are operator-authored in config (safe); placeholders are replaced with values
 * from LuckPerms meta and (when available) PlaceholderAPI. The click action is ALWAYS
 * suggest_command to {@code /w <name> } and is hardcoded here so nothing from a player can
 * inject a run_command or other click action.
 */
public final class PlayerHoverService {

    private final LPC plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final PlaceholderAPIHook papi;

    private volatile boolean enabled;
    private volatile List<String> hoverLines = List.of();
    private volatile boolean clickEnabled;

    public PlayerHoverService(LPC plugin, PlaceholderAPIHook papi) {
        this.plugin = plugin;
        this.papi = papi;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("player-hover.enabled", true);
        this.hoverLines = c.getStringList("player-hover.lines");
        if (hoverLines.isEmpty()) {
            hoverLines = List.of(
                    "<gradient:#FED83D:#BE2086>{name}</gradient>",
                    "<gray>Rank: <white>{prefix}</white>",
                    "<gray>World: <white>{world}</white>",
                    "",
                    "<yellow>Click to message"
            );
        }
        this.clickEnabled = c.getBoolean("player-hover.click-to-message", true);
    }

    public boolean isEnabled() { return enabled; }

    /**
     * Wraps a player's display name with server-controlled hover (and click-to-message)
     * for display in the rendered chat line.
     */
    public Component apply(Player player, Component displayName) {
        if (!enabled) return displayName;

        CachedMetaData meta = LuckPermsProvider.get().getPlayerAdapter(Player.class).getMetaData(player);
        String prefix = meta.getPrefix() != null ? meta.getPrefix() : "";
        String suffix = meta.getSuffix() != null ? meta.getSuffix() : "";
        World world = player.getWorld();

        StringBuilder hoverText = new StringBuilder();
        for (int i = 0; i < hoverLines.size(); i++) {
            if (i > 0) hoverText.append("\n");
            hoverText.append(hoverLines.get(i));
        }

        // Build hover text with safe placeholders
        String template = hoverText.toString()
                .replace("{name}", player.getName())
                .replace("{displayname}", "<displayname>")
                .replace("{prefix}", prefix)
                .replace("{suffix}", suffix)
                .replace("{world}", world.getName());

        // Apply PlaceholderAPI only to the operator-authored template (trusted), not to player content.
        if (papi.isAvailable()) {
            template = papi.setPlaceholders(player, template);
        }

        TagResolver displaynameResolver = Placeholder.component("displayname", displayName);
        Component hoverComponent;
        try {
            hoverComponent = mm.deserialize(template, displaynameResolver);
        } catch (Exception e) {
            hoverComponent = Component.text(player.getName());
        }

        Component name = displayName.hoverEvent(HoverEvent.showText(hoverComponent));
        if (clickEnabled) {
            name = name.clickEvent(ClickEvent.suggestCommand("/w " + player.getName() + " "));
        }
        return name;
    }
}
