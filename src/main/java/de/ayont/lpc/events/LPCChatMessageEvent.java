package de.ayont.lpc.events;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired (asynchronously) after moderation passes but BEFORE final rendering for a global chat
 * message. Other plugins can cancel the message, mutate the plain-text content, or decorate the
 * final component.
 */
public class LPCChatMessageEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private String rawMessage;
    private Component finalComponent;
    private boolean cancelled;

    public LPCChatMessageEvent(boolean async, Player player, String rawMessage) {
        super(async);
        this.player = player;
        this.rawMessage = rawMessage;
    }

    public Player getPlayer() { return player; }
    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; }
    public Component getFinalComponent() { return finalComponent; }
    public void setFinalComponent(Component finalComponent) { this.finalComponent = finalComponent; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
