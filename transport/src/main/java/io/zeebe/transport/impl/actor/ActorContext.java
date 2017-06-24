package io.zeebe.transport.impl.actor;

import java.util.concurrent.CompletableFuture;

import io.zeebe.transport.TransportListener;
import io.zeebe.transport.impl.TransportChannel;

public abstract class ActorContext
{
    private Conductor conductor;
    private Sender sender;
    private Receiver receiver;

    public void setConductor(Conductor clientConductor)
    {
        this.conductor = clientConductor;
    }

    public void setSender(Sender sender)
    {
        this.sender = sender;
    }

    public void setReceiver(Receiver receiver)
    {
        this.receiver = receiver;
    }

    public void registerChannel(TransportChannel ch)
    {
        sender.registerChannel(ch);
        receiver.registerChannel(ch);
    }

    public void removeChannel(TransportChannel ch)
    {
        sender.removeChannel(ch);
        receiver.removeChannel(ch);
    }

    public void removeListener(TransportListener listener)
    {
        conductor.removeListener(listener);
    }

    public CompletableFuture<Void> registerListener(TransportListener channelListener)
    {
        return conductor.registerListener(channelListener);
    }

    public CompletableFuture<Void> onClose()
    {
        return conductor.onClose();
    }

    public CompletableFuture<Void> closeAllOpenChannels()
    {
        return conductor.closeCurrentChannels();
    }

    public CompletableFuture<Void> interruptAllChannels()
    {
        return conductor.interruptAllChannels();
    }

}
