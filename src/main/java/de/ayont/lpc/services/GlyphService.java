package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import de.ayont.lpc.hooks.NexoHook;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Glyph/emoji replacement service. Wraps the existing simple {@code emoji.replacements} config
 * (literal text replacements) and, when Nexo is installed, also supports the richer
 * {@code glyphs.registry} section for :alias: → {@code <glyph:name>} replacements.
 * <p>
 * Security: replacement text is either a literal unicode character (legacy emoji) or a glyph tag
 * parsed with a restricted MiniMessage parser that excludes click/hover/insertion events (see
 * {@link NexoHook#apply}). The player's raw text is NEVER parsed with the full MiniMessage parser.
 */
public final class GlyphService {

    private final LPC plugin;
    private final NexoHook nexoHook;

    private volatile boolean enabled;
    private volatile boolean requirePermission;
    private volatile Map<String, String> literalReplacements = Map.of();

    public GlyphService(LPC plugin, NexoHook nexoHook) {
        this.plugin = plugin;
        this.nexoHook = nexoHook;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.enabled = c.getBoolean("glyphs.enabled", true);
        if (!this.enabled) {
            this.literalReplacements = Map.of();
            nexoHook.loadRegistry(Map.of());
            return;
        }
        // Literal glyph replacements from the new glyphs.replacements section only.
        // Legacy emoji.shortcuts are handled by EmojiReplacer (so existing configs keep working).
        Map<String, String> literal = new LinkedHashMap<>();

        ConfigurationSection gsec = c.getConfigurationSection("glyphs.replacements");
        if (gsec != null) {
            for (String key : gsec.getKeys(false)) {
                String value = gsec.getString(key);
                if (value != null && !key.isEmpty()) literal.put(key, value);
            }
        }

        // New glyphs.registry (with aliases)
        Map<String, List<String>> registryMap = new LinkedHashMap<>();
        ConfigurationSection reg = c.getConfigurationSection("glyphs.registry");
        if (reg != null) {
            for (String glyphName : reg.getKeys(false)) {
                ConfigurationSection entry = reg.getConfigurationSection(glyphName);
                if (entry == null) continue;
                List<String> aliases = entry.getStringList("aliases");
                String fallback = entry.getString("fallback");
                if (fallback != null && !fallback.isEmpty() && !nexoHook.isAvailable()) {
                    literal.putIfAbsent(":" + glyphName + ":", fallback);
                    for (String alias : aliases) literal.putIfAbsent(alias, fallback);
                }
                if (nexoHook.isAvailable()) {
                    registryMap.put(glyphName, aliases);
                }
            }
        }
        nexoHook.loadRegistry(registryMap);
        this.literalReplacements = Map.copyOf(literal);
        this.requirePermission = c.getBoolean("glyphs.require-permission", false);
    }

    public boolean isEnabled() { return enabled; }
    public boolean isNexoAvailable() { return nexoHook.isAvailable(); }

    /** Apply glyph/emoji replacements to a player's message component. */
    public Component applyForPlayer(Player player, Component message) {
        if (!enabled) return message;
        if (requirePermission && !player.hasPermission("lpc.emoji") && !player.hasPermission("lpc.glyph")) {
            return message;
        }
        // 1. literal emoji replacements first (unicode / text)
        Component out = message;
        for (var e : literalReplacements.entrySet()) {
            out = out.replaceText(b -> b.matchLiteral(e.getKey()).replacement(e.getValue()));
        }
        // 2. Nexo glyph tags
        if (nexoHook.isAvailable()) {
            out = nexoHook.apply(out, Map.of());
        }
        return out;
    }
}
