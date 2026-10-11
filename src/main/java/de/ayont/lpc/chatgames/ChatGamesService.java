package de.ayont.lpc.chatgames;

import de.ayont.lpc.LPC;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automatic chat mini-games. One game at a time, ticked once per second on the global region;
 * answers arrive on the async chat thread and are claimed with a CAS so exactly one player wins.
 */
public final class ChatGamesService {

    public static final List<String> TYPES = List.of("math", "scramble", "type", "number", "trivia", "complete");

    public enum Result { NONE, CONSUMED }

    public record Stat(String name, int wins) {}

    private record Game(String type, String question, String hint, Set<String> answers, String shownAnswer,
                        long target, long startedAt, AtomicBoolean claimed) {}

    private final LPC plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, Stat> stats = new ConcurrentHashMap<>();
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastWin = new ConcurrentHashMap<>();

    private volatile ConfigurationSection cfg;
    private volatile Game current;
    private volatile long nextStartAt;
    private ScheduledTask task;

    public ChatGamesService(LPC plugin) {
        this.plugin = plugin;
        reload();
        loadStats();
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> tick(), 40L, 20L);
    }

    public void reload() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("chat-games");
        this.cfg = c != null ? c : plugin.getConfig().createSection("chat-games");
        this.nextStartAt = System.currentTimeMillis() + intervalMs();
    }

    public void shutdown() {
        if (task != null) task.cancel();
    }

    private boolean enabled() { return cfg.getBoolean("enabled", true); }
    private long intervalMs() { return Math.max(1, cfg.getLong("interval-minutes", 10)) * 60_000L; }

    // ── Tick (global region) ────────────────────────────────────────────────

    private void tick() {
        long now = System.currentTimeMillis();
        Game g = current;
        if (g != null) {
            if (now - g.startedAt >= cfg.getLong("duration-seconds", 60) * 1000L && g.claimed.compareAndSet(false, true)) {
                current = null;
                nextStartAt = now + intervalMs();
                broadcast(msg("timeout"), Placeholder.unparsed("answer", g.shownAnswer));
            }
            return;
        }
        if (enabled() && now >= nextStartAt) {
            if (players().size() >= cfg.getInt("min-players", 3)) start(null);
            else nextStartAt = now + 60_000L; // re-check in a minute
        }
    }

    /** Starts a game of the given type (random enabled type when null). Returns false if one is running. */
    public boolean start(String type) {
        if (current != null) return false;
        List<String> enabledTypes = cfg.getStringList("types").stream()
                .map(s -> s.toLowerCase(Locale.ROOT)).filter(TYPES::contains).toList();
        if (enabledTypes.isEmpty()) enabledTypes = TYPES;
        String t = type != null ? type : enabledTypes.get(ThreadLocalRandom.current().nextInt(enabledTypes.size()));
        Game g = create(t);
        if (g == null) return false;
        current = g;
        broadcast(msg("start." + t),
                Placeholder.unparsed("question", g.question),
                Placeholder.unparsed("hint", g.hint),
                Placeholder.unparsed("seconds", Long.toString(cfg.getLong("duration-seconds", 60))));
        playSound("sounds.start");
        return true;
    }

    public boolean stop() {
        Game g = current;
        if (g == null || !g.claimed.compareAndSet(false, true)) return false;
        current = null;
        nextStartAt = System.currentTimeMillis() + intervalMs();
        broadcast(msg("stopped"), Placeholder.unparsed("answer", g.shownAnswer));
        return true;
    }

    private Game create(String type) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long now = System.currentTimeMillis();
        switch (type) {
            case "math" -> {
                ChatGameLogic.MathProblem p = ChatGameLogic.math(rnd, cfg.getInt("math.max-add", 500), cfg.getInt("math.max-mul", 15));
                String ans = ChatGameLogic.formatNumber(p.answer());
                return new Game(type, p.text(), "", Set.of(), ans, p.answer(), now, new AtomicBoolean());
            }
            case "number" -> {
                int max = Math.max(2, cfg.getInt("number.max", 100));
                long n = 1 + rnd.nextInt(max);
                return new Game(type, "1-" + max, "", Set.of(), Long.toString(n), n, now, new AtomicBoolean());
            }
            case "trivia" -> {
                List<Map<?, ?>> list = cfg.getMapList("trivia");
                if (list.isEmpty()) return null;
                Map<?, ?> q = list.get(rnd.nextInt(list.size()));
                List<String> answers = new ArrayList<>();
                if (q.get("answers") instanceof List<?> l) l.forEach(a -> answers.add(String.valueOf(a)));
                if (answers.isEmpty()) return null;
                return new Game(type, String.valueOf(q.get("question")), "", normalized(answers), answers.get(0), 0, now, new AtomicBoolean());
            }
            default -> {
                List<String> pool = cfg.getStringList(type.equals("type") ? "phrases" : "words");
                if (pool.isEmpty()) return null;
                String w = pool.get(rnd.nextInt(pool.size()));
                String shown = switch (type) {
                    case "scramble" -> ChatGameLogic.scramble(w.toLowerCase(Locale.ROOT), rnd);
                    case "complete" -> ChatGameLogic.mask(w.toLowerCase(Locale.ROOT), cfg.getDouble("complete.hide-ratio", 0.4), rnd);
                    default -> w;
                };
                String hint = w.isEmpty() ? "" : w.substring(0, 1).toUpperCase(Locale.ROOT);
                return new Game(type, shown, hint, normalized(List.of(w)), w, 0, now, new AtomicBoolean());
            }
        }
    }

    private static Set<String> normalized(List<String> answers) {
        return answers.stream().map(ChatGameLogic::normalize).filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    // ── Answers (async chat thread) ─────────────────────────────────────────

    /** Checks a chat message against the running game. CONSUMED = cancel the chat message. */
    public Result handle(Player player, String raw) {
        Game g = current;
        if (g == null || g.claimed.get() || hidden.contains(player.getUniqueId())) return Result.NONE;
        boolean numeric = g.type.equals("math") || g.type.equals("number");
        if (numeric) {
            Long n = ChatGameLogic.parseNumber(raw);
            if (n == null) return Result.NONE;
            if (n != g.target) {
                if (g.type.equals("number")) {
                    sendTo(player, msg(n < g.target ? "number-higher" : "number-lower"),
                            Placeholder.unparsed("guess", ChatGameLogic.formatNumber(n)));
                    return Result.CONSUMED;
                }
                return Result.NONE;
            }
        } else if (!g.answers.contains(ChatGameLogic.normalize(raw))) {
            return Result.NONE;
        }
        // Correct answer from here on: never let it reach public chat.
        Long last = lastWin.get(player.getUniqueId());
        long cooldown = cfg.getLong("win-cooldown-minutes", 15) * 60_000L;
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldown) {
            sendTo(player, msg("cooldown"), Placeholder.unparsed("minutes",
                    Long.toString(Math.max(1, (cooldown - (now - last) + 59_999) / 60_000))));
            return Result.CONSUMED;
        }
        if (!g.claimed.compareAndSet(false, true)) return Result.CONSUMED;
        lastWin.put(player.getUniqueId(), now);
        long elapsed = now - g.startedAt;
        plugin.getScheduler().run(() -> win(player, g, elapsed));
        return Result.CONSUMED;
    }

    private void win(Player player, Game g, long elapsed) {
        current = null;
        nextStartAt = System.currentTimeMillis() + intervalMs();
        List<String> rewardTexts = giveRewards(player, g.type);
        int wins = addWin(player);
        broadcast(msg("win"),
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("time", ChatGameLogic.formatSeconds(elapsed)),
                Placeholder.unparsed("answer", g.shownAnswer),
                Placeholder.unparsed("wins", ChatGameLogic.formatNumber(wins)),
                Placeholder.parsed("rewards", String.join("<dark_gray>, </dark_gray>", rewardTexts)));
        playSound("sounds.win");
    }

    private List<String> giveRewards(Player player, String type) {
        List<Map<?, ?>> entries = cfg.getMapList("rewards." + type);
        if (entries.isEmpty()) entries = cfg.getMapList("rewards.default");
        List<String> texts = new ArrayList<>();
        for (Map<?, ?> e : entries) {
            double chance = e.get("chance") instanceof Number n ? n.doubleValue() : 100.0;
            if (ThreadLocalRandom.current().nextDouble(100.0) >= chance) continue;
            if (e.get("commands") instanceof List<?> cmds) {
                for (Object c : cmds) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.valueOf(c).replace("{player}", player.getName()));
                }
            }
            if (e.get("text") != null) texts.add(String.valueOf(e.get("text")));
        }
        return texts;
    }

    // ── Stats & toggle ──────────────────────────────────────────────────────

    private void loadStats() {
        var db = plugin.getDatabaseService();
        db.executeAsync(c -> {
            try (var st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS chatgames (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, "
                        + "wins INTEGER DEFAULT 0, hidden INTEGER DEFAULT 0)");
                try (var rs = st.executeQuery("SELECT uuid, name, wins, hidden FROM chatgames")) {
                    while (rs.next()) {
                        UUID id = UUID.fromString(rs.getString(1));
                        stats.put(id, new Stat(rs.getString(2), rs.getInt(3)));
                        if (rs.getInt(4) != 0) hidden.add(id);
                    }
                }
            }
        });
    }

    private int addWin(Player p) {
        Stat s = stats.merge(p.getUniqueId(), new Stat(p.getName(), 1), (a, b) -> new Stat(b.name(), a.wins() + 1));
        save(p.getUniqueId(), s.name(), s.wins(), hidden.contains(p.getUniqueId()));
        return s.wins();
    }

    /** Toggles game messages for the player; returns true when games are now visible. */
    public boolean toggle(Player p) {
        boolean nowHidden = !hidden.remove(p.getUniqueId());
        if (nowHidden) hidden.add(p.getUniqueId());
        save(p.getUniqueId(), p.getName(), wins(p.getUniqueId()), nowHidden);
        return !nowHidden;
    }

    private void save(UUID id, String name, int wins, boolean isHidden) {
        plugin.getDatabaseService().executeAsync(c -> {
            try (var ps = c.prepareStatement("INSERT INTO chatgames (uuid, name, wins, hidden) VALUES (?,?,?,?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, wins=excluded.wins, hidden=excluded.hidden")) {
                ps.setString(1, id.toString());
                ps.setString(2, name);
                ps.setInt(3, wins);
                ps.setInt(4, isHidden ? 1 : 0);
                ps.executeUpdate();
            }
        });
    }

    public int wins(UUID id) {
        Stat s = stats.get(id);
        return s == null ? 0 : s.wins();
    }

    public List<Stat> top(int n) {
        return stats.values().stream().filter(s -> s.wins() > 0)
                .sorted(Comparator.comparingInt(Stat::wins).reversed()).limit(n).toList();
    }

    public boolean isRunning() { return current != null; }

    // ── Messaging ───────────────────────────────────────────────────────────

    public String msg(String key) { return cfg.getString("messages." + key, ""); }

    public Component render(String template, TagResolver... extra) {
        TagResolver[] all = new TagResolver[extra.length + 1];
        all[0] = Placeholder.parsed("prefix", cfg.getString("messages.prefix", ""));
        System.arraycopy(extra, 0, all, 1, extra.length);
        return mm.deserialize(template, all);
    }

    public void sendTo(org.bukkit.command.CommandSender to, String template, TagResolver... extra) {
        if (template == null || template.isEmpty()) return;
        plugin.send(to, render(template, extra));
    }

    private List<Player> players() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (!hidden.contains(p.getUniqueId())) out.add(p);
        return out;
    }

    private void broadcast(String template, TagResolver... extra) {
        if (template == null || template.isEmpty()) return;
        Component c = render(template, extra);
        for (Player p : players()) plugin.send(p, c);
        plugin.send(Bukkit.getConsoleSender(), c);
    }

    private void playSound(String path) {
        String sound = cfg.getString(path, "");
        if (sound.isEmpty()) return;
        for (Player p : players()) {
            try { p.playSound(p.getLocation(), sound, 1f, 1f); } catch (Exception ignored) {}
        }
    }
}
