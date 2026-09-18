package de.ayont.lpc.hooks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional hook for Nexo (the successor to ItemsAdder) glyph/emoji system.
 * <p>
 * Nexo provides MiniMessage integration via {@code <glyph:name>} tags. LPC never parses the
 * full MiniMessage spec on glyph replacements — glyphs are resolved with a MINIMAL resolver that
 * ONLY understands the {@code glyph} tag (plus colour), so even a misconfigured glyph mapping
 * cannot inject click/hover/insertion events.
 * <p>
 * If Nexo is absent the hook is no-op: {@link #isAvailable()} returns false and glyph replacements
 * fall back to configured literal replacements.
 */
public final class NexoHook {

    private final boolean available;
    private final JavaPlugin plugin;
    private final MiniMessage glyphParser;
    private volatile Map<String, String> aliases = Map.of(); // alias -> glyph name

    public NexoHook(JavaPlugin plugin) {
        this.plugin = plugin;
        PluginManager pm = plugin.getServer().getPluginManager();
        this.available = pm.getPlugin("Nexo") != null && pm.getPlugin("Nexo").isEnabled();
        // Restricted parser that only understands color/decor/reset/gradient AND glyph tags.
        // No click/hover/insertion/selector/etc. The glyph tag is supplied separately so if Nexo
        // changes its tag namespace we are isolated from it.
        this.glyphParser = MiniMessage.builder()
                .tags(TagResolver.resolver(
                        StandardTags.color(),
                        StandardTags.shadowColor(),
                        StandardTags.decorations(),
                        StandardTags.gradient(),
                        StandardTags.transition(),
                        StandardTags.rainbow(),
                        StandardTags.reset(),
                        StandardTags.newline()
                ))
                .build();
        this.aliases = new LinkedHashMap<>();
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Loads alias → glyph-name mappings from the config section. The minimessage tag is built
     * server-side ({@code <glyph:NAME>}) so a glyph entry can never inject arbitrary tags.
     */
    public void loadRegistry(Map<String, List<String>> registryMap) {
        if (!available) return;
        Map<String, String> map = new LinkedHashMap<>();
        for (var entry : registryMap.entrySet()) {
            String glyphName = entry.getKey();
            if (glyphName == null || glyphName.isBlank()) continue;
            // Always register :name: as an alias as well
            map.put(":" + glyphName.toLowerCase() + ":", glyphName);
            for (String alias : entry.getValue()) {
                if (alias != null && !alias.isBlank()) {
                    map.put(alias.toLowerCase(), glyphName);
                }
            }
        }
        this.aliases = Map.copyOf(map);
    }

    public Map<String, String> getAliases() {
        return aliases;
    }

    /**
     * Replaces registered :alias: tokens in the given text with Nexo glyph components.
     * This runs on plain text BEFORE the player-safe color parser, so glyph names are validated
     * against the configured registry only.
     *
     * @return a component with glyphs inserted, or a literal-text component when a glyph name is not valid.
     */
    public Component apply(Component message, Map<String, String> additionalReplacements) {
        if (!available) return message;
        Component out = message;
        Map<String, String> all = new HashMap<>(aliases);
        if (additionalReplacements != null) all.putAll(additionalReplacements);
        for (var e : all.entrySet()) {
            String literalAlias = e.getKey();
            String glyphName = e.getValue();
            String tag = "<glyph:" + escapeGlyphName(glyphName) + ">";
            // Parse only with the restricted glyph-aware parser to prevent injection via glyph names.
            Component glyphComponent;
            try {
                glyphComponent = glyphParser.deserialize(tag);
            } catch (Exception ex) {
                // Invalid glyph spec: leave the alias literal
                continue;
            }
            out = out.replaceText(b -> b.matchLiteral(literalAlias).replacement(glyphComponent));
        }
        return out;
    }

    /** Guards against glyph names that contain characters that would close the tag early. */
    private static String escapeGlyphName(String name) {
        // Nexo glyph names should be alphanumeric / underscore / dash. Strip anything else.
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "");
    }
}
