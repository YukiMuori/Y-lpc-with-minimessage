package de.ayont.lpc.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Fired when a player adds or removes another player from their ignore list. */
public class LPCIgnoreEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public enum Action { ADD, REMOVE }

    private final Player player;
    private final UUID target;
    private final String targetName;
    private final Action action;
    private boolean cancelled;

    public LPCIgnoreEvent(Player player, UUID target, String targetName, Action action) {
        this.player = player;
        this.target = target;
        this.targetName = targetName;
        this.action = action;
    }

    public Player getPlayer() { return player; }
    public UUID getTarget() { return target; }
    public String getTargetName() { return targetName; }
    public Action getAction() { return action; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
