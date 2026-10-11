package de.ayont.lpc.chat;

import de.ayont.lpc.LPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.regex.Pattern;

/**
 * Replaces {@code [i]} / {@code [item]} tokens in the player's chat text with a rich item
 * preview that, on Paper 1.21.5+, uses Minecraft's native <sprite:...> glyph so the actual
 * in-game item icon shows up inline, followed by the item's name and a hover tooltip showing
 * the real item.
 *
 * <p>Security: item display names are sanitized ({@link ComponentSanitizer#stripInteractive});
 * the MiniMessage string we build comes entirely from server-side data (material key) so a
 * player can never inject tags through this path.
 */
public final class ItemShowService {

    private static final Pattern ITEM_PATTERN = Pattern.compile("\\[(?:i|item)]", Pattern.CASE_INSENSITIVE);

    static final String DEFAULT_FORMAT = "<dark_gray>⟦</dark_gray>{item}{amount}<dark_gray>⟧</dark_gray>";
    static final String DEFAULT_AMOUNT = " <gray>×<white>{amount}</white></gray>";
    static final String DEFAULT_EMPTY = "<dark_gray>⟦</dark_gray><gray>a mani vuote</gray><dark_gray>⟧</dark_gray>";

    private final LPC plugin;
    private volatile boolean enabled;
    private volatile boolean useSprite;
    private volatile String color;
    private volatile String format;
    private volatile String amountFormat;
    private volatile String emptyHand;

    public ItemShowService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("item-show.enabled", true);
        // Sprite rendering requires Paper 1.21.5+ (Adventure 4.18+ has sprite; Paper includes
        // the sprite font at minecraft:item). Fall back to text-only when unavailable.
        this.useSprite = cfg.getBoolean("item-show.sprite", true) && plugin.isPaper() && hasSpriteSupport();
        this.color = cfg.getString("item-show.color", "<aqua>");
        this.format = cfg.getString("item-show.format", DEFAULT_FORMAT);
        this.amountFormat = cfg.getString("item-show.amount-format", DEFAULT_AMOUNT);
        this.emptyHand = cfg.getString("item-show.empty-hand", DEFAULT_EMPTY);
    }

    public boolean isEnabled() { return enabled; }

    public static boolean containsToken(String text) {
        return text != null && ITEM_PATTERN.matcher(text).find();
    }

    /** Replace all {@code [i]}/{@code [item]} tokens in {@code rendered} with an item preview. */
    public Component apply(Player player, Component rendered, boolean withHover) {
        if (!enabled) return rendered;
        if (!player.hasPermission("lpc.itemplaceholder")) return rendered;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            return rendered.replaceText(TextReplacementConfig.builder()
                    .match(ITEM_PATTERN)
                    .replacement(isolate(mm(emptyHand)))
                    .build());
        }

        Component icon = buildIcon(item, withHover);
        return rendered.replaceText(TextReplacementConfig.builder()
                .match(ITEM_PATTERN)
                .replacement(icon)
                .build());
    }

    private Component buildIcon(ItemStack item, boolean withHover) {
        Component name = itemName(item);

        // Inline sprite (Paper 1.21.5+)
        Component spritePart;
        if (useSprite) {
            String spriteTag = buildSpriteTag(item.getType());
            try {
                spritePart = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize(spriteTag);
            } catch (Exception ex) {
                spritePart = Component.text("● ");
            }
        } else {
            spritePart = Component.empty();
        }

        Component coloredName = mm(color).append(name).colorIfAbsent(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false);
        Component full = format(format, amountFormat, Component.empty().append(spritePart).append(coloredName),
                item.getAmount());

        if (withHover && plugin.isPaper()) {
            full = full.hoverEvent(item.asHoverEvent());
        }
        return full;
    }

    /** Builds the final component; {item}/{amount} are placeholders, amount only shown when &gt; 1. */
    static Component format(String format, String amountFormat, Component item, int amount) {
        Component amt = amount > 1
                ? mm(amountFormat.replace("{amount}", "<n>"), Placeholder.unparsed("n", String.valueOf(amount)))
                : Component.empty();
        Component full = mm(format.replace("{item}", "<item>").replace("{amount}", "<amount>"),
                Placeholder.component("item", item), Placeholder.component("amount", amt));
        return isolate(full);
    }

    /** Explicit white + no decorations so the surrounding message/nick style is not inherited. */
    static Component isolate(Component c) {
        Component base = Component.empty().color(NamedTextColor.WHITE);
        for (TextDecoration d : TextDecoration.values()) base = base.decoration(d, false);
        return base.append(c);
    }

    private Component itemName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            if (plugin.isPaper()) {
                Component c = meta.displayName();
                if (c != null) {
                    // Player-influenced (anvil/other plugins): strip interactive tags
                    return ComponentSanitizer.stripInteractive(c);
                }
            } else {
                return LPC.getLegacySerializer().deserialize(meta.getDisplayName());
            }
        }
        // Default: use the vanilla translated name so it respects client language.
        // Paper 1.21+ has item.translationKey() — fall back to readable english for older builds.
        try {
            String key = (String) Material.class.getMethod("translationKey").invoke(item.getType());
            return Component.translatable(key);
        } catch (Exception ex) {
            return Component.text(humanReadable(item.getType()));
        }
    }

    /**
     * Builds a MiniMessage tag referencing the item sprite. Paper ships a font at
     * {@code minecraft:item} containing every item icon; on older builds the sprite
     * lookup is skipped by {@link #hasSpriteSupport()}.
     */
    private String buildSpriteTag(Material mat) {
        // Paper 1.21.5+ exposes sprites under key "item/<namespace>/<path>"
        String matKey = mat.getKey().asString(); // e.g. "minecraft:diamond_sword"
        return "<font:minecraft:item><sprite:" + matKey + "></font> ";
    }

    private boolean hasSpriteSupport() {
        try {
            // Sprite component type was added in Adventure 4.18; detect via builder method.
            Class.forName("net.kyori.adventure.text.ScoreComponent"); // sanity — we want a concrete check
            // Probe for the Sprite component class
            Class.forName("net.kyori.adventure.text.SpriteComponent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static String humanReadable(Material m) {
        String n = m.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c == ' ') { cap = true; sb.append(c); continue; }
            sb.append(cap ? Character.toUpperCase(c) : c);
            cap = false;
        }
        return sb.toString();
    }

    private static Component mm(String text, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... r) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(text, r);
    }
}
