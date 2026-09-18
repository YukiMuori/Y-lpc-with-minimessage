package de.ayont.lpc.events;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a player sends a message in staff-chat mode or via /sc <msg>. */
public class LPCStaffChatEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private String rawMessage;
    private Component rendered;
    private boolean cancelled;

    public LPCStaffChatEvent(boolean async, Player player, String rawMessage) {
        super(async);
        this.player = player;
        this.rawMessage = rawMessage;
    }

    public Player getPlayer() { return player; }
    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String m) { this.rawMessage = m; }
    public Component getRendered() { return rendered; }
    public void setRendered(Component c) { this.rendered = c; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
