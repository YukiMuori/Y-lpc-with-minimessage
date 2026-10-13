package de.ayont.lpc.moderation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure (Bukkit-free) text analysis used by {@link ModerationService}. Every method operates on raw
 * plaintext only and has no side effects, so the moderation rules are fully unit-testable and can
 * run safely on the async chat thread. All patterns here are constant (never built from player
 * input) to avoid ReDoS.
 */
public final class TextChecks {

    // A bare "word.word" is only a link when the TLD is a known one (avoids "ciao.come stai").
    private static final String DOT = "(?:\\.|\\s?[\\(\\[]dot[\\)\\]]\\s?|\\s+dot\\s+)";
    private static final String TLDS = "(?:it|com|net|org|gg|io|tv|eu|xyz|club|info|biz|us|uk|de|fr|es|ru|ly|cc|online|site|live"
            + "|fun|top|app|dev|gl|be|tk|ml|ga|cf|gq|lol|store|shop|link|cloud|host|pw|ovh)";
    private static final Pattern URL = Pattern.compile(
            "(?i)(?:https?://\\S+|www\\.\\S+|(?<![\\w.@-])(?:[a-z0-9-]+" + DOT + ")+" + TLDS + "(?![a-z0-9])(?:/\\S*)?)");
    private static final Pattern DOT_VARIANT = Pattern.compile("(?i)\\s?[\\(\\[]dot[\\)\\]]\\s?|\\s+dot\\s+");
    private static final Pattern IP = Pattern.compile(
            "(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?");

    private TextChecks() {
    }

    /** @return the fraction (0..1) of letters that are uppercase; 0 when there are no letters. */
    public static double uppercaseRatio(String text) {
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        return letters == 0 ? 0.0 : (double) upper / letters;
    }

    /** @return true if the text is long enough and its uppercase ratio exceeds the threshold. */
    public static boolean isShout(String text, int minLength, double maxRatio) {
        return text.length() >= minLength && uppercaseRatio(text) > maxRatio;
    }

    /**
     * Normalises text for fuzzy matching: lowercases, maps common leetspeak to letters, strips
     * non-alphanumerics, and collapses runs of the same character. Used for both profanity detection
     * and repeat detection so {@code "F U  C K!!"} and {@code "fuuuck"} normalise alike.
     */
    public static String normalize(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        char previous = 0;
        for (int i = 0; i < lower.length(); i++) {
            char mapped = switch (lower.charAt(i)) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '5', '$' -> 's';
                case '7' -> 't';
                default -> lower.charAt(i);
            };
            if (Character.isLetterOrDigit(mapped) && mapped != previous) {
                sb.append(mapped);
                previous = mapped;
            } else if (!Character.isLetterOrDigit(mapped)) {
                previous = 0;
            }
        }
        return sb.toString();
    }

    /** @return true if the normalised message contains any of the (already-normalised) words. */
    public static boolean containsAny(String normalizedMessage, Collection<String> normalizedWords) {
        for (String word : normalizedWords) {
            if (!word.isEmpty() && normalizedMessage.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Masks each occurrence of a blocked word in the original text with {@code maskChar} repeated to
     * the word's length. Matching is case-insensitive on the literal word.
     */
    public static String maskWords(String original, Collection<String> words, char maskChar) {
        String result = original;
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            String mask = String.valueOf(maskChar).repeat(word.length());
            result = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE)
                    .matcher(result)
                    .replaceAll(Matcher.quoteReplacement(mask));
        }
        return result;
    }

    /** @return true if the text contains a non-allowlisted URL (or IP, when {@code detectIp}). */
    public static boolean containsAdvert(String text, boolean detectIp, Collection<String> allowlist) {
        Matcher urlMatcher = URL.matcher(text);
        while (urlMatcher.find()) {
            if (!isAllowlisted(urlMatcher.group(), allowlist)) {
                return true;
            }
        }
        if (detectIp) {
            Matcher ipMatcher = IP.matcher(text);
            while (ipMatcher.find()) {
                if (!isAllowlisted(ipMatcher.group(), allowlist)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Replaces every non-allowlisted URL/IP with {@code replacement}. */
    public static String maskAdvert(String text, boolean detectIp, Collection<String> allowlist, String replacement) {
        String result = replaceMatches(URL.matcher(text), allowlist, replacement);
        if (detectIp) {
            result = replaceMatches(IP.matcher(result), allowlist, replacement);
        }
        return result;
    }

    private static String replaceMatches(Matcher matcher, Collection<String> allowlist, String replacement) {
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String match = matcher.group();
            matcher.appendReplacement(sb, isAllowlisted(match, allowlist)
                    ? Matcher.quoteReplacement(match)
                    : Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** Lowercase, no scheme, no "www.", "(dot)" folded to "." so hosts compare reliably. */
    private static String canonical(String link) {
        String m = DOT_VARIANT.matcher(link.trim().toLowerCase(Locale.ROOT)).replaceAll(".");
        m = m.replaceFirst("^https?://", "");
        return m.startsWith("www.") ? m.substring(4) : m;
    }

    /**
     * Host-aware allowlist: an entry without "/" allows that host and its subdomains; an entry with a
     * path allows that path and anything below it. "evil.com/?neverlanditalia.it" is NOT allowed.
     */
    private static boolean isAllowlisted(String match, Collection<String> allowlist) {
        String m = canonical(match);
        int cut = m.indexOf('/');
        String host = (cut < 0 ? m : m.substring(0, cut));
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        for (String entry : allowlist) {
            String a = canonical(entry);
            if (a.isEmpty()) continue;
            if (a.indexOf('/') >= 0) {
                if (m.equals(a) || m.startsWith(a + "/") || m.startsWith(a + "?")) return true;
            } else if (host.equals(a) || host.endsWith("." + a)) {
                return true;
            }
        }
        return false;
    }

    // ---- Profanity (word-boundary regex built from plain words) ----

    private static String letterClass(char c) {
        return switch (c) {
            case 'a' -> "[a4@àá*]";
            case 'e' -> "[e3èé*]";
            case 'i' -> "[i1!|ìí*]";
            case 'o' -> "[o0òó*]";
            case 'u' -> "[uùúü*]";
            case 'c' -> "[ck(]";
            case 's' -> "[s5$]";
            case 't' -> "[t7]";
            case 'g' -> "[g9]";
            default -> Pattern.quote(String.valueOf(c));
        };
    }

    /**
     * Builds one pattern per word. Letters match leet variants and may be repeated ("cazzzo"), one
     * separator may sit between letters ("c.a.z.z.o"), a space in the entry matches a short gap, a
     * trailing '*' accepts any ending ("stronz*"). The word must not touch other letters, so
     * "scusa" or "analisi" never match.
     */
    public static List<Pattern> buildProfanityPatterns(Collection<String> words) {
        List<Pattern> out = new ArrayList<>();
        for (String raw : words) {
            String w = raw.trim().toLowerCase(Locale.ROOT);
            boolean stem = w.endsWith("*");
            if (stem) w = w.substring(0, w.length() - 1);
            if (w.isEmpty()) continue;
            StringBuilder sb = new StringBuilder("(?<![\\p{L}\\p{N}])");
            for (int i = 0; i < w.length(); i++) {
                char c = w.charAt(i);
                if (c == ' ') {
                    sb.append("[\\s.,_*-]{0,3}");
                    continue;
                }
                sb.append(letterClass(c)).append('+');
                if (i + 1 < w.length() && w.charAt(i + 1) != ' ') sb.append("[.,_*-]?");
            }
            sb.append(stem ? "[\\p{L}\\p{N}*]*" : "(?![\\p{L}])");
            out.add(Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
        return out;
    }

    /** Replaces every match with maskChar repeated to the matched length (same length, no leaks). */
    public static String maskProfanity(String text, List<Pattern> patterns, char maskChar) {
        String result = text;
        for (Pattern p : patterns) {
            Matcher m = p.matcher(result);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(maskChar).repeat(m.end() - m.start())));
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    /** Shortens runs of the same character ("ciaooooooo" -> "ciaooo" with max 3). max &lt;= 0 = off. */
    public static String collapseRuns(String text, int max) {
        if (max <= 0) return text;
        StringBuilder sb = new StringBuilder(text.length());
        int run = 0;
        char prev = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            run = (c == prev) ? run + 1 : 1;
            prev = c;
            if (run <= max) sb.append(c);
        }
        return sb.toString();
    }
}
