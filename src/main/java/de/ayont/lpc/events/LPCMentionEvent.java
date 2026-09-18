package de.ayont.lpc.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a player is about to be notified of a mention (@player, @staff, or @everyone). */
public class LPCMentionEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public enum Type { PLAYER, STAFF, EVERYONE }

    private final Player sender;
    private final Player target;
    private final Type type;
    private boolean cancelled;

    public LPCMentionEvent(boolean async, Player sender, Player target, Type type) {
        super(async);
        this.sender = sender;
        this.target = target;
        this.type = type;
    }

    public Player getSender() { return sender; }
    public Player getTarget() { return target; }
    public Type getType() { return type; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
