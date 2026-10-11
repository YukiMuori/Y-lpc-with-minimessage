package de.ayont.lpc.chatgames;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ChatGameLogicTest {

    @Test
    void normalizeIgnoresCaseAccentsAndPunctuation() {
        assertEquals("perche si", ChatGameLogic.normalize("  Perché,   SÌ! "));
        assertEquals("drago dell end", ChatGameLogic.normalize("Drago dell'End"));
        assertEquals("bambu", ChatGameLogic.normalize("Bambù"));
        assertEquals("-5", ChatGameLogic.normalize("-5"));
        assertEquals("", ChatGameLogic.normalize(null));
    }

    @Test
    void scrambleIsDifferentAnagram() {
        Random rnd = new Random(42);
        for (String w : new String[]{"creeper", "ab", "netherite"}) {
            String s = ChatGameLogic.scramble(w, rnd);
            assertNotEquals(w, s);
            assertTrue(ChatGameLogic.isAnagram(w, s));
        }
        assertEquals("aaa", ChatGameLogic.scramble("aaa", rnd)); // impossible to differ, must not loop
    }

    @Test
    void maskKeepsFirstLetterAndLength() {
        String m = ChatGameLogic.mask("diamante", 0.4, new Random(1));
        assertEquals(8, m.length());
        assertEquals('d', m.charAt(0));
        assertTrue(m.contains("_"));
    }

    @Test
    void mathAnswersAreCorrect() {
        Random rnd = new Random(7);
        for (int i = 0; i < 300; i++) {
            ChatGameLogic.MathProblem p = ChatGameLogic.math(rnd, 500, 15);
            String[] parts = p.text().split(" ");
            long a = ChatGameLogic.parseNumber(parts[0]), b = ChatGameLogic.parseNumber(parts[2]);
            long expected = switch (parts[1]) { case "+" -> a + b; case "-" -> a - b; default -> a * b; };
            assertEquals(expected, p.answer(), p.text());
            assertTrue(p.answer() >= 0);
        }
    }

    @Test
    void numbersInItalianFormat() {
        assertEquals("1.234", ChatGameLogic.formatNumber(1234));
        assertEquals(1234L, ChatGameLogic.parseNumber("1.234"));
        assertEquals(-5L, ChatGameLogic.parseNumber(" -5 "));
        assertNull(ChatGameLogic.parseNumber("ciao"));
        assertEquals("4,2", ChatGameLogic.formatSeconds(4230));
    }
}
