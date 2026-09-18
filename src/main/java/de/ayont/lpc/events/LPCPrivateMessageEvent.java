package de.ayont.lpc.events;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired asynchronously when a private message (/msg, /w, /tell, /r) is sent. */
public class LPCPrivateMessageEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player sender;
    private final Player receiver;
    private String rawMessage;
    private Component renderedSender;
    private Component renderedReceiver;
    private boolean cancelled;
    private String cancelReason;

    public LPCPrivateMessageEvent(boolean async, Player sender, Player receiver, String rawMessage) {
        super(async);
        this.sender = sender;
        this.receiver = receiver;
        this.rawMessage = rawMessage;
    }

    public Player getSender() { return sender; }
    public Player getReceiver() { return receiver; }
    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String msg) { this.rawMessage = msg; }
    public Component getRenderedSender() { return renderedSender; }
    public void setRenderedSender(Component c) { this.renderedSender = c; }
    public Component getRenderedReceiver() { return renderedReceiver; }
    public void setRenderedReceiver(Component c) { this.renderedReceiver = c; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
