package de.ayont.lpc;

import de.ayont.lpc.chat.ChatFormatService;
import de.ayont.lpc.chat.EmojiReplacer;
import de.ayont.lpc.chat.ItemPlaceholder;
import de.ayont.lpc.chat.ItemShowService;
import de.ayont.lpc.chat.MentionService;
import de.ayont.lpc.chat.UrlLinkifier;
import de.ayont.lpc.commands.LPCCommand;
import de.ayont.lpc.commands.MessageCommands;
import de.ayont.lpc.commands.AnnounceCommand;
import de.ayont.lpc.commands.IgnoreCommand;
import de.ayont.lpc.commands.InboxCommand;
import de.ayont.lpc.commands.QuickChatCommands;
import de.ayont.lpc.database.DatabaseService;
import de.ayont.lpc.discord.ConfiguredDiscordService;
import de.ayont.lpc.discord.DiscordService;
import de.ayont.lpc.hooks.NexoHook;
import de.ayont.lpc.hooks.PlaceholderAPIHook;
import de.ayont.lpc.hooks.VanishService;
import de.ayont.lpc.listener.AsyncChatListener;
import de.ayont.lpc.listener.ConnectionListener;
import de.ayont.lpc.listener.SpigotChatListener;
import de.ayont.lpc.listener.PlayerQuitListener;
import de.ayont.lpc.moderation.ModerationService;
import de.ayont.lpc.moderation.MuteService;
import de.ayont.lpc.scheduler.Scheduler;
import de.ayont.lpc.scheduler.Schedulers;
import de.ayont.lpc.services.AnnouncementService;
import de.ayont.lpc.services.BossBarService;
import de.ayont.lpc.services.ClearChatService;
import de.ayont.lpc.services.GlyphService;
import de.ayont.lpc.services.IgnoreService;
import de.ayont.lpc.services.InboxService;
import de.ayont.lpc.services.JoinLeaveService;
import de.ayont.lpc.services.MentionExtensionService;
import de.ayont.lpc.services.NotificationService;
import de.ayont.lpc.services.PlayerHoverService;
import de.ayont.lpc.services.PlayerSettingsService;
import de.ayont.lpc.services.PrivateMessageService;
import de.ayont.lpc.services.SlowModeService;
import de.ayont.lpc.services.StaffChatService;
import de.ayont.lpc.services.StatisticsService;
import de.ayont.lpc.update.UpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class LPC extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private boolean paper;
    private boolean folia;
    private Scheduler scheduler;
    private ChatFormatService chatFormatService;
    private MuteService muteService;
    private ModerationService moderationService;
    private EmojiReplacer emojiReplacer;
    private UrlLinkifier urlLinkifier;
    private MentionService mentionService;
    private ItemShowService itemShowService;

    // V2 services
    private VanishService vanishService;
    private PlaceholderAPIHook placeholderApiHook;
    private NexoHook nexoHook;
    private DatabaseService databaseService;
    private NotificationService notificationService;
    private IgnoreService ignoreService;
    private PlayerSettingsService playerSettingsService;
    private PrivateMessageService privateMessageService;
    private StaffChatService staffChatService;
    private SlowModeService slowModeService;
    private ClearChatService clearChatService;
    private GlyphService glyphService;
    private StatisticsService statisticsService;
    private MentionExtensionService mentionExtensionService;
    private PlayerHoverService playerHoverService;
    private DiscordService discordService;
    private ConfiguredDiscordService configuredDiscordService;
    private BossBarService bossBarService;
    private JoinLeaveService joinLeaveService;
    private InboxService inboxService;
    private AnnouncementService announcementService;

    private BukkitTask inboxTickTask;

    public static LegacyComponentSerializer getLegacySerializer() {
        return LEGACY_SERIALIZER;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.paper = detectPaper();
        this.folia = detectFolia();
        this.scheduler = Schedulers.create(this);

        // Hooks (detect optional plugins)
        this.vanishService = VanishService.detect(getServer().getPluginManager());
        this.placeholderApiHook = PlaceholderAPIHook.create(this);
        this.nexoHook = new NexoHook(this);

        // Core chat services (from V1)
        this.chatFormatService = new ChatFormatService(this);
        this.muteService = new MuteService(this);
        this.moderationService = new ModerationService(this, muteService);
        this.emojiReplacer = new EmojiReplacer(this);
        this.urlLinkifier = new UrlLinkifier(this);
        this.mentionService = new MentionService(this);
        this.itemShowService = new ItemShowService(this);

        // V2 services
        this.databaseService = new DatabaseService(this);
        databaseService.initialize();
        this.playerSettingsService = new PlayerSettingsService();
        this.notificationService = new NotificationService(this);
        this.bossBarService = new BossBarService(this);
        this.ignoreService = new IgnoreService(this);
        this.joinLeaveService = new JoinLeaveService(this);
        this.inboxService = new InboxService(this);
        this.privateMessageService = new PrivateMessageService(this);
        this.staffChatService = new StaffChatService(this);
        this.slowModeService = new SlowModeService(this);
        this.clearChatService = new ClearChatService(this);
        this.glyphService = new GlyphService(this, nexoHook);
        this.statisticsService = new StatisticsService(this, databaseService);
        this.mentionExtensionService = new MentionExtensionService(this);
        this.playerHoverService = new PlayerHoverService(this, placeholderApiHook);
        this.configuredDiscordService = new ConfiguredDiscordService(this);
        this.discordService = configuredDiscordService;
        this.announcementService = new AnnouncementService(this);

        registerCommand();
        registerListeners();
        startInboxTicker();
        startUpdateChecker();
        logRuntimePlatform();
        logIntegrations();
    }

    private void logIntegrations() {
        if (!"none".equals(vanishService.providerName())) {
            getLogger().info("Vanish integration enabled: " + vanishService.providerName());
        }
        if (nexoHook.isAvailable()) {
            getLogger().info("Nexo detected. Glyph integration enabled.");
        }
        if (placeholderApiHook.isAvailable()) {
            getLogger().info("PlaceholderAPI detected.");
        }
        if (itemShowService.isEnabled() && paper) {
            getLogger().info("Item sprite rendering enabled (use [i] or [item] in chat).");
        }
        if (announcementService != null) {
            getLogger().info("Auto-announcements system loaded from announcements.yml.");
        }
    }

    private void logRuntimePlatform() {
        getLogger().info("Running on " + getServer().getName() + " (API " + getServer().getBukkitVersion()
                + ") on Java " + System.getProperty("java.version")
                + (paper ? " [Paper/Adventure chat]" : " [Spigot/legacy chat]")
                + (folia ? " [Folia]" : ""));
    }

    /** Send a one-time hint to players who try [i] without permission. */
    public void maybeItemPlaceholderHint(Player player, String message) {
        if (!itemShowService.isEnabled()) return;
        if (player.hasPermission("lpc.itemplaceholder")) return;
        if (!ItemShowService.containsToken(message) && !ItemPlaceholder.containsToken(message)) return;
        send(player, MiniMessage.miniMessage().deserialize(
                "<dark_gray>[<gradient:#B754F4:#FC00FF>LPC</gradient>] <yellow>Chiedi a un admin il permesso "
                        + "<white>lpc.itemplaceholder</white> per usare <white>[i]</white> in chat."));
    }

    public boolean isPaper() { return paper; }
    public boolean isFolia() { return folia; }
    public Scheduler getScheduler() { return scheduler; }

    @Override
    public void onDisable() {
        if (announcementService != null) announcementService.shutdown();
        if (discordService != null) discordService.shutdown();
        if (databaseService != null) databaseService.close();
        if (scheduler != null) scheduler.cancelAll();
        if (inboxTickTask != null) { inboxTickTask.cancel(); inboxTickTask = null; }
    }

    public ChatFormatService getChatFormatService() { return chatFormatService; }
    public ModerationService getModerationService() { return moderationService; }
    public MuteService getMuteService() { return muteService; }
    public EmojiReplacer getEmojiReplacer() { return emojiReplacer; }
    public UrlLinkifier getUrlLinkifier() { return urlLinkifier; }
    public MentionService getMentionService() { return mentionService; }
    public ItemShowService getItemShowService() { return itemShowService; }

    public VanishService getVanishService() { return vanishService; }
    public PlaceholderAPIHook getPlaceholderApiHook() { return placeholderApiHook; }
    public NexoHook getNexoHook() { return nexoHook; }
    public DatabaseService getDatabaseService() { return databaseService; }
    public NotificationService getNotificationService() { return notificationService; }
    public IgnoreService getIgnoreService() { return ignoreService; }
    public PlayerSettingsService getPlayerSettingsService() { return playerSettingsService; }
    public PrivateMessageService getPrivateMessageService() { return privateMessageService; }
    public StaffChatService getStaffChatService() { return staffChatService; }
    public SlowModeService getSlowModeService() { return slowModeService; }
    public ClearChatService getClearChatService() { return clearChatService; }
    public GlyphService getGlyphService() { return glyphService; }
    public StatisticsService getStatisticsService() { return statisticsService; }
    public MentionExtensionService getMentionExtensionService() { return mentionExtensionService; }
    public PlayerHoverService getPlayerHoverService() { return playerHoverService; }
    public DiscordService getDiscordService() { return discordService; }
    public BossBarService getBossBarService() { return bossBarService; }
    public JoinLeaveService getJoinLeaveService() { return joinLeaveService; }
    public InboxService getInboxService() { return inboxService; }
    public AnnouncementService getAnnouncementService() { return announcementService; }

    public void setDiscordService(DiscordService service) {
        this.discordService = service == null ? DiscordService.disabled() : service;
    }

    /** Re-reads config-derived state for every service. */
    public void reloadServices() {
        reloadConfig();
        chatFormatService.reload();
        muteService.reload();
        moderationService.reload();
        emojiReplacer.reload();
        urlLinkifier.reload();
        mentionService.reload();
        itemShowService.reload();
        notificationService.reload();
        bossBarService.reload();
        ignoreService.reload();
        joinLeaveService.reload();
        inboxService.reload();
        privateMessageService.reload();
        staffChatService.reload();
        slowModeService.reload();
        clearChatService.reload();
        glyphService.reload();
        statisticsService.reload();
        mentionExtensionService.reload();
        playerHoverService.reload();
        if (configuredDiscordService != null) configuredDiscordService.reload();
        if (announcementService != null) announcementService.reload();
    }

    public boolean isDisabledWorld(String worldName) {
        for (String world : getConfig().getStringList("disabled-worlds")) {
            if (world.equalsIgnoreCase(worldName)) return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    public Component displayNameOf(Player player) {
        return paper ? player.displayName() : LEGACY_SERIALIZER.deserialize(player.getDisplayName());
    }

    public void send(CommandSender target, Component component) {
        if (paper) {
            target.sendMessage(component);
        } else {
            target.sendMessage(LEGACY_SERIALIZER.serialize(component));
        }
    }

    private void registerCommand() {
        PluginCommand command = getCommand("lpc");
        if (command == null) {
            getLogger().warning("Command 'lpc' is missing from plugin.yml; commands are unavailable.");
            return;
        }
        LPCCommand executor = new LPCCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        MessageCommands.register(this);
        IgnoreCommand.register(this);
        QuickChatCommands.register(this);
        AnnounceCommand.register(this);

        // Alias /messaggi → /lpc inbox
        PluginCommand inbox = getCommand("inbox");
        if (inbox != null) {
            inbox.setExecutor(new InboxCommand(this));
            inbox.setTabCompleter(new InboxCommand(this));
        }
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        if (paper) {
            pm.registerEvents(new AsyncChatListener(this), this);
        } else {
            pm.registerEvents(new SpigotChatListener(this), this);
        }
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new PlayerQuitListener(this), this);
    }

    private void startInboxTicker() {
        // Run inbox tick every 30s on main thread
        inboxTickTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (inboxService != null) inboxService.tick();
        }, 60L, 600L);
    }

    private void startUpdateChecker() {
        if (!getConfig().getBoolean("update-checker", true)) return;
        UpdateChecker updateChecker = new UpdateChecker(this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        updateChecker.checkAsync();
    }

    private boolean detectPaper() {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            getLogger().info("Paper API detected — using Adventure chat rendering.");
            return true;
        } catch (ClassNotFoundException notPaper) {
            getLogger().info("Spigot API detected — using legacy chat rendering.");
            return false;
        }
    }

    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            getLogger().info("Folia detected — using regionized scheduling.");
            return true;
        } catch (ClassNotFoundException notFolia) {
            return false;
        }
    }
}
