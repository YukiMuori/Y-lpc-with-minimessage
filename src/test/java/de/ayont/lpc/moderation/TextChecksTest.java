package de.ayont.lpc.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextChecksTest {

    @Test
    @DisplayName("uppercaseRatio counts only letters")
    void uppercaseRatio_ignoresNonLetters() {
        assertEquals(1.0, TextChecks.uppercaseRatio("AB!! 12"));
        assertEquals(0.0, TextChecks.uppercaseRatio("12345"));
        assertEquals(0.5, TextChecks.uppercaseRatio("Ab"));
    }

    @Test
    @DisplayName("isShout requires both length and ratio")
    void isShout_lengthAndRatio() {
        assertTrue(TextChecks.isShout("STOP RIGHT THERE", 8, 0.6));
        assertFalse(TextChecks.isShout("HI", 8, 0.6));
        assertFalse(TextChecks.isShout("this is calm enough", 8, 0.6));
    }

    @Test
    @DisplayName("normalize folds leetspeak, case, spacing and repeats")
    void normalize_foldsObfuscation() {
        assertEquals("helo", TextChecks.normalize("Heeello"));
        assertEquals("helo", TextChecks.normalize("h3LL0"));
        assertEquals("youareanob", TextChecks.normalize("you are a n00b!"));
    }

    @Test
    @DisplayName("containsAny matches normalised blocked words")
    void containsAny_matchesNormalised() {
        List<String> words = List.of(TextChecks.normalize("noob"));
        assertTrue(TextChecks.containsAny(TextChecks.normalize("you n0ob"), words));
        assertFalse(TextChecks.containsAny(TextChecks.normalize("you are nice"), words));
    }

    @Test
    @DisplayName("maskWords replaces blocked words case-insensitively")
    void maskWords_replacesLiteral() {
        assertEquals("Hello ****", TextChecks.maskWords("Hello NOOB", List.of("noob"), '*'));
    }

    @Test
    @DisplayName("containsAdvert detects non-allowlisted URLs and respects allowlist")
    void containsAdvert_urlAndAllowlist() {
        assertTrue(TextChecks.containsAdvert("join evil.net now", false, List.of()));
        assertFalse(TextChecks.containsAdvert("see youtube.com/watch", false, List.of("youtube.com")));
        assertFalse(TextChecks.containsAdvert("no links here", false, List.of()));
    }

    @Test
    @DisplayName("containsAdvert optionally detects IPs")
    void containsAdvert_ip() {
        assertTrue(TextChecks.containsAdvert("connect 192.168.0.1:25565", true, List.of()));
        assertFalse(TextChecks.containsAdvert("connect 192.168.0.1", false, List.of()));
    }

    @Test
    @DisplayName("maskAdvert redacts non-allowlisted links only")
    void maskAdvert_redactsLinks() {
        assertEquals("join [link] now", TextChecks.maskAdvert("join evil.net now", false, List.of(), "[link]"));
        assertEquals("watch youtube.com today",
                TextChecks.maskAdvert("watch youtube.com today", false, List.of("youtube.com"), "[link]"));
    }

    private static final List<java.util.regex.Pattern> PROF = TextChecks.buildProfanityPatterns(
            List.of("cazzo", "stronz*", "merda", "culo", "porco dio", "vaffanculo", "fuck*", "puttan*"));

    @Test
    @DisplayName("profanity: variants masked, normal words untouched")
    void profanity_variantsAndFalsePositives() {
        assertEquals("che ***** fai", TextChecks.maskProfanity("che cazzo fai", PROF, '*'));
        assertEquals("che ***** fai", TextChecks.maskProfanity("che c4zz0 fai", PROF, '*'));
        assertEquals("sei *******", TextChecks.maskProfanity("sei STRONZA", PROF, '*'));
        assertEquals("********", TextChecks.maskProfanity("porcodio", PROF, '*'));
        assertEquals("*******", TextChecks.maskProfanity("c.u.l.o.", PROF, '*').substring(0, 7));
        for (String ok : List.of("scusa", "analisi", "classe", "articolo", "ridicolo", "secolo", "cultura",
                "scusate la merenda", "il mio pc ha un bug", "ciao a tutti", "tazza", "mandarino")) {
            assertEquals(ok, TextChecks.maskProfanity(ok, PROF, '*'));
        }
    }

    @Test
    @DisplayName("advert: known TLDs only, host-aware allowlist")
    void advert_allowlistHostAware() {
        List<String> allow = List.of("neverlanditalia.it", "twitch.tv/davetoy95");
        assertFalse(TextChecks.containsAdvert("ciao.come stai", true, allow));
        assertFalse(TextChecks.containsAdvert("vai su discord.neverlanditalia.it", true, allow));
        assertFalse(TextChecks.containsAdvert("https://www.neverlanditalia.it/wiki", true, allow));
        assertFalse(TextChecks.containsAdvert("twitch.tv/davetoy95", true, allow));
        assertTrue(TextChecks.containsAdvert("twitch.tv/altro", true, allow));
        assertTrue(TextChecks.containsAdvert("evil.com/?neverlanditalia.it", true, allow));
        assertTrue(TextChecks.containsAdvert("fakeneverlanditalia.it", true, allow));
        assertTrue(TextChecks.containsAdvert("entra su play (dot) server dot net", true, allow));
        assertTrue(TextChecks.containsAdvert("ip 51.38.12.9:25565", true, allow));
        assertTrue(TextChecks.containsAdvert("www.sito.boh", true, allow));
        assertFalse(TextChecks.containsAdvert("alle 10.30 ci vediamo", true, allow));
    }

    @Test
    @DisplayName("collapseRuns shortens repeated characters")
    void collapseRuns_works() {
        assertEquals("ciaooo", TextChecks.collapseRuns("ciaooooooo", 3));
        assertEquals("ciaoooooo", TextChecks.collapseRuns("ciaoooooo", 0));
    }
}
