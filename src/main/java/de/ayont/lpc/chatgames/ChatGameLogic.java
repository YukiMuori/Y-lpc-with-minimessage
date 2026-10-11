package de.ayont.lpc.chatgames;

import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

/** Pure, Bukkit-free helpers for ChatGames (unit-tested). */
public final class ChatGameLogic {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^a-z0-9\\- ]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private ChatGameLogic() {}

    /** Lower-case, accent-free, punctuation-free, single-spaced. "  Perché, Sì! " -> "perche si". */
    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        n = MARKS.matcher(n).replaceAll("");
        n = NOT_ALLOWED.matcher(n.replace('\'', ' ')).replaceAll("");
        return SPACES.matcher(n).replaceAll(" ").trim();
    }

    /** Shuffles the letters of each word; guarantees a different result when possible. */
    public static String scramble(String word, Random rnd) {
        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) chars.add(c);
        boolean canDiffer = word.chars().distinct().count() > 1;
        String out;
        int tries = 0;
        do {
            Collections.shuffle(chars, rnd);
            StringBuilder sb = new StringBuilder();
            for (char c : chars) sb.append(c);
            out = sb.toString();
        } while (canDiffer && out.equals(word) && ++tries < 50);
        return out;
    }

    /** True when {@code a} is a permutation of the letters of {@code b} (ignoring case). */
    public static boolean isAnagram(String a, String b) {
        char[] x = a.toLowerCase(Locale.ROOT).toCharArray();
        char[] y = b.toLowerCase(Locale.ROOT).toCharArray();
        java.util.Arrays.sort(x);
        java.util.Arrays.sort(y);
        return java.util.Arrays.equals(x, y);
    }

    /** Hides ~ratio of the letters (never the first one, never spaces) with '_'. */
    public static String mask(String word, double ratio, Random rnd) {
        char[] c = word.toCharArray();
        List<Integer> idx = new ArrayList<>();
        for (int i = 1; i < c.length; i++) if (Character.isLetterOrDigit(c[i])) idx.add(i);
        Collections.shuffle(idx, rnd);
        int hide = Math.max(1, (int) Math.round(idx.size() * ratio));
        for (int i = 0; i < Math.min(hide, idx.size()); i++) c[idx.get(i)] = '_';
        return new String(c);
    }

    /** A random arithmetic problem: text uses ×, the answer is exact. */
    public record MathProblem(String text, long answer) {}

    public static MathProblem math(Random rnd, int maxAdd, int maxMul) {
        switch (rnd.nextInt(3)) {
            case 0 -> {
                int a = 1 + rnd.nextInt(maxAdd), b = 1 + rnd.nextInt(maxAdd);
                return new MathProblem(formatNumber(a) + " + " + formatNumber(b), (long) a + b);
            }
            case 1 -> {
                int a = 1 + rnd.nextInt(maxAdd), b = 1 + rnd.nextInt(maxAdd);
                int hi = Math.max(a, b), lo = Math.min(a, b);
                return new MathProblem(formatNumber(hi) + " - " + formatNumber(lo), (long) hi - lo);
            }
            default -> {
                int a = 2 + rnd.nextInt(Math.max(1, maxMul - 1)), b = 2 + rnd.nextInt(Math.max(1, maxMul - 1));
                return new MathProblem(a + " × " + b, (long) a * b);
            }
        }
    }

    /** Parses a whole number typed by a player ("1.234", "1234", "-5"); null if not a number. */
    public static Long parseNumber(String raw) {
        String s = raw == null ? "" : raw.trim().replace(".", "").replace(" ", "");
        if (!s.matches("-?\\d{1,15}")) return null;
        return Long.parseLong(s);
    }

    /** Italian grouping: 1234 -> "1.234". */
    public static String formatNumber(long n) {
        return NumberFormat.getIntegerInstance(Locale.ITALY).format(n);
    }

    /** Seconds with one decimal, Italian style: 4230 ms -> "4,2". */
    public static String formatSeconds(long millis) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.ITALY);
        f.setMinimumFractionDigits(1);
        f.setMaximumFractionDigits(1);
        return f.format(millis / 1000.0);
    }
}
