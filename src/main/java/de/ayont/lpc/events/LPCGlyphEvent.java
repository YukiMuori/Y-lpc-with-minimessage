package de.ayont.lpc.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a player uses a configured glyph alias (emoji) in chat. */
public class LPCGlyphEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String glyphName;
    private boolean cancelled;

    public LPCGlyphEvent(boolean async, Player player, String glyphName) {
        super(async);
        this.player = player;
        this.glyphName = glyphName;
    }

    public Player getPlayer() { return player; }
    public String getGlyphName() { return glyphName; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
