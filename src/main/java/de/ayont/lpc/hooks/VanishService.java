package de.ayont.lpc.hooks;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Abstraction over vanish plugins so LPC does not hard-depend on any single one.
 * <p>
 * Implementations detect popular vanish plugins (e.g. EssentialsX vanish, PremiumVanish/SuperVanish,
 * CMI) via reflection against their public APIs and delegate the isVanished check. If no supported
 * plugin is installed the default {@link #isVanished(Player)} returns false, meaning every online
 * player is treated as visible.
 */
public interface VanishService {

    /** @return true if the player is currently vanished from regular players. */
    boolean isVanished(Player player);

    /** @return the name of the backing vanish plugin (e.g. "Essentials", "SuperVanish") or "none". */
    String providerName();

    /** No-op service used when no vanish plugin is installed. */
    static VanishService none() {
        return new VanishService() {
            @Override public boolean isVanished(Player player) { return false; }
            @Override public String providerName() { return "none"; }
        };
    }

    /**
     * Attempts to hook into a supported vanish plugin on the server. Returns {@link #none()}
     * when no known plugin is present.
     */
    static VanishService detect(org.bukkit.plugin.PluginManager pm) {
        // EssentialsX — check via Essentials.getUser(player).isVanished()
        Plugin essentials = pm.getPlugin("Essentials");
        if (essentials != null && essentials.isEnabled()) {
            try {
                return new EssentialsVanishService(essentials);
            } catch (Throwable t) {
                // fall through to next attempt
            }
        }
        // SuperVanish / PremiumVanish — use de.myzelyam.api.vanish.VanishAPI
        Plugin sv = pm.getPlugin("SuperVanish");
        if (sv == null) sv = pm.getPlugin("PremiumVanish");
        if (sv != null && sv.isEnabled()) {
            try {
                return new SuperVanishService(sv);
            } catch (Throwable t) {
                // fall through
            }
        }
        // CMI
        Plugin cmi = pm.getPlugin("CMI");
        if (cmi != null && cmi.isEnabled()) {
            try {
                return new CMIVanishService(cmi);
            } catch (Throwable t) {
                // fall through
            }
        }
        return none();
    }

    /** EssentialsX vanish integration. */
    class EssentialsVanishService implements VanishService {
        private final Plugin plugin;
        EssentialsVanishService(Plugin p) { this.plugin = p; }
        @Override
        public boolean isVanished(Player player) {
            try {
                Object essentials = plugin.getClass().getMethod("getUser", Player.class).invoke(plugin, player);
                if (essentials == null) return false;
                return (boolean) essentials.getClass().getMethod("isVanished").invoke(essentials);
            } catch (Throwable t) {
                return false;
            }
        }
        @Override public String providerName() { return "EssentialsX"; }
    }

    /** SuperVanish / PremiumVanish integration via public API. */
    class SuperVanishService implements VanishService {
        private final Object api;
        SuperVanishService(Plugin p) throws Exception {
            this.api = Class.forName("de.myzelyam.api.vanish.VanishAPI")
                    .getMethod("getInstance").invoke(null);
        }
        @Override
        public boolean isVanished(Player player) {
            try {
                return (boolean) api.getClass().getMethod("isInvisible", Player.class).invoke(api, player);
            } catch (Throwable t) {
                return false;
            }
        }
        @Override public String providerName() { return "SuperVanish/PremiumVanish"; }
    }

    /** CMI vanish integration. */
    class CMIVanishService implements VanishService {
        private final Plugin plugin;
        CMIVanishService(Plugin p) { this.plugin = p; }
        @Override
        public boolean isVanished(Player player) {
            try {
                Object cmi = Class.forName("com.Zrips.CMI.CMI").getMethod("getInstance").invoke(null);
                Object um = cmi.getClass().getMethod("getUsersManager").invoke(cmi);
                Object user = um.getClass().getMethod("getUser", Player.class).invoke(um, player);
                if (user == null) return false;
                return (boolean) user.getClass().getMethod("isVanished").invoke(user);
            } catch (Throwable t) {
                return false;
            }
        }
        @Override public String providerName() { return "CMI"; }
    }
}
