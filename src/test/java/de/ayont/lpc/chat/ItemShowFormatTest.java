package de.ayont.lpc.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ItemShowFormatTest {
    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void amountShownOnlyAboveOne() {
        Component item = Component.text("Elisir Epico", NamedTextColor.LIGHT_PURPLE);
        String one = plain(ItemShowService.format(ItemShowService.DEFAULT_FORMAT, ItemShowService.DEFAULT_AMOUNT, item, 1));
        String three = plain(ItemShowService.format(ItemShowService.DEFAULT_FORMAT, ItemShowService.DEFAULT_AMOUNT, item, 3));
        assertEquals("⟦Elisir Epico⟧", one);
        assertEquals("⟦Elisir Epico ×3⟧", three);
    }

    @Test
    void doesNotInheritMessageStyle() {
        Component c = ItemShowService.format(ItemShowService.DEFAULT_FORMAT, ItemShowService.DEFAULT_AMOUNT,
                Component.text("x"), 1);
        assertEquals(NamedTextColor.WHITE, c.color());
        assertSame(TextDecoration.State.FALSE, c.decoration(TextDecoration.BOLD));
        assertSame(TextDecoration.State.FALSE, c.decoration(TextDecoration.ITALIC));
        assertFalse(plain(c).contains("[ "));
    }
}
