package de.ayont.lpc.services;

import de.ayont.lpc.LPC;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Automatic broadcast (announcement) system inspired by InfiniteAnnouncements.
 *
 * <p>Loads from {@code announcements.yml} (separate from config.yml). Each announcement can
 * target one or more channels: {@code chat}, {@code actionbar}, {@code bossbar}, {@code title},
 * and can be gated by permission, world list, sound and weight.
 */
public final class AnnouncementService {

    private final LPC plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Random random = new Random();

    private final Map<java.util.UUID, BossBar> activeBars = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile long defaultIntervalTicks;
    private volatile boolean randomOrder;
    private volatile List<Announcement> announcements = List.of();
    private final AtomicInteger index = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AnnouncementService(LPC plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        shutdown();
        saveDefault();
        File f = new File(plugin.getDataFolder(), "announcements.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        this.enabled = cfg.getBoolean("enabled", true);
        this.defaultIntervalTicks = Math.max(60L, cfg.getLong("interval-seconds", 180)) * 20L;
        this.randomOrder = cfg.getBoolean("random", false);

        List<Announcement> list = new ArrayList<>();
        ConfigurationSection s = cfg.getConfigurationSection("announcements");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                ConfigurationSection a = s.getConfigurationSection(key);
                if (a == null) continue;
                Announcement an = parse(a);
                if (an != null) list.add(an);
            }
        }
        this.announcements = List.copyOf(list);
        this.index.set(0);

        if (enabled && !announcements.isEmpty()) {
            running.set(true);
            scheduleNext(defaultIntervalTicks);
        }
    }

    private Announcement parse(ConfigurationSection a) {
        List<String> lines = a.getStringList("lines");
        String line = a.getString("message");
        if (lines.isEmpty() && line != null && !line.isEmpty()) lines = List.of(line);
        if (lines.isEmpty()) {
            String t = a.getString("text");
            if (t != null && !t.isEmpty()) lines = List.of(t);
        }
        if (lines.isEmpty()) return null;
        String channel = a.getString("channel", "chat").toLowerCase();
        String permission = a.getString("permission", null);
        List<String> worlds = a.getStringList("worlds");
        String sound = a.getString("sound", null);
        float soundVol = (float) a.getDouble("sound-volume", 1.0);
        float soundPitch = (float) a.getDouble("sound-pitch", 1.0);
        String bossColor = a.getString("bossbar.color", "yellow");
        String bossOverlay = a.getString("bossbar.overlay", "progress");
        float bossProgress = (float) a.getDouble("bossbar.progress", 1.0);
        double bossSeconds = a.getDouble("bossbar.seconds", 4.0);
        String subtitle = a.getString("subtitle", null);
        int titleFadeIn = a.getInt("title-fade-in", 10);
        int titleStay = a.getInt("title-stay", 60);
        int titleFadeOut = a.getInt("title-fade-out", 10);
        String prefix = a.getString("prefix", "<dark_gray>[<gold>Annuncio</gold>]</dark_gray> ");
        int weight = Math.max(1, a.getInt("weight", 1));
        long interval = a.getLong("interval-seconds", 0) * 20L;
        return new Announcement(lines, channel, permission, new HashSet<>(worlds), sound, soundVol, soundPitch,
                bossColor, bossOverlay, bossProgress, bossSeconds,
                subtitle, titleFadeIn, titleStay, titleFadeOut, prefix, weight,
                interval > 0 ? interval : defaultIntervalTicks);
    }

    private void saveDefault() {
        if (!new File(plugin.getDataFolder(), "announcements.yml").exists()) {
            plugin.saveResource("announcements.yml", false);
        }
    }

    private void scheduleNext(long delayTicks) {
        if (!running.get()) return;
        plugin.getScheduler().runDelayed(this::tick, delayTicks);
    }

    private void tick() {
        if (!running.get() || announcements.isEmpty()) return;
        Announcement a;
        if (randomOrder) {
            a = announcements.get(random.nextInt(announcements.size()));
        } else {
            int i = index.getAndUpdate(v -> (v + 1) % announcements.size());
            a = announcements.get(i);
        }
        broadcast(a);
        scheduleNext(a.intervalTicks);
    }

    public void broadcast(Announcement a) {
        plugin.getScheduler().run(() -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (a.permission != null && !a.permission.isEmpty() && !p.hasPermission(a.permission)) continue;
                if (!a.worlds.isEmpty() && !a.worlds.contains(p.getWorld().getName())) continue;
                deliver(p, a);
            }
        });
    }

    private void deliver(Player p, Announcement a) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < a.lines.size(); i++) {
            if (i > 0) joined.append("\n");
            joined.append(a.lines.get(i));
        }
        String raw = joined.toString();
        Component msg = mm.deserialize(raw);

        switch (a.channel) {
            case "actionbar" -> {
                if (plugin.isPaper()) p.sendActionBar(msg);
                else plugin.send(p, msg);
            }
            case "title" -> {
                if (plugin.isPaper()) {
                    Component sub = a.subtitle != null ? mm.deserialize(a.subtitle) : Component.empty();
                    Title.Times t = Title.Times.times(
                            Duration.ofMillis(a.titleFadeIn * 50L),
                            Duration.ofMillis(a.titleStay * 50L),
                            Duration.ofMillis(a.titleFadeOut * 50L));
                    p.showTitle(Title.title(msg, sub, t));
                } else plugin.send(p, msg);
            }
            case "bossbar" -> {
                if (plugin.isPaper()) {
                    BossBar oldBar = activeBars.remove(p.getUniqueId());
                    if (oldBar != null) p.hideBossBar(oldBar);
                    BossBar bar = BossBar.bossBar(msg, a.bossProgress,
                            BossBarService.color(a.bossColor), BossBarService.overlay(a.bossOverlay));
                    activeBars.put(p.getUniqueId(), bar);
                    p.showBossBar(bar);
                    plugin.getScheduler().runDelayed(() -> {
                        activeBars.remove(p.getUniqueId(), bar);
                        if (p.isOnline()) p.hideBossBar(bar);
                    }, Math.max(20L, (long) (a.bossSeconds * 20L)));
                } else plugin.send(p, msg);
            }
            default -> {
                Component chat = a.prefix.isEmpty() ? msg : mm.deserialize(a.prefix).append(msg);
                plugin.send(p, chat);
            }
        }

        if (a.sound != null && !a.sound.isEmpty()) {
            try { p.playSound(p.getLocation(), a.sound, a.soundVol, a.soundPitch); }
            catch (Exception ignored) {}
        }
    }

    public void shutdown() {
        running.set(false);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            BossBar bar = activeBars.remove(p.getUniqueId());
            if (bar != null && plugin.isPaper()) p.hideBossBar(bar);
        }
    }

    @SuppressWarnings("checkstyle:RecordComponentNumber")
    public record Announcement(
            List<String> lines, String channel, String permission, Set<String> worlds,
            String sound, float soundVol, float soundPitch,
            String bossColor, String bossOverlay, float bossProgress, double bossSeconds,
            String subtitle, int titleFadeIn, int titleStay, int titleFadeOut,
            String prefix, int weight, long intervalTicks) {}
}
