package io.zeebe.transport;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import io.zeebe.transport.impl.ServerSocketBinding;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.ActorContext;

public class ServerTransport implements AutoCloseable
{
    protected final ServerOutput output;
    protected final ActorContext transportActorContext;
    protected final TransportContext transportContext;
    protected final ServerSocketBinding serverSocketBinding;

    public ServerTransport(ActorContext transportActorContext, TransportContext transportContext)
    {
        this.transportActorContext = transportActorContext;
        this.transportContext = transportContext;
        this.output = transportContext.getServerOutput();
        this.serverSocketBinding = transportContext.getServerSocketBinding();
    }

    public ServerOutput getOutput()
    {
        return output;
    }

    public CompletableFuture<Void> registerChannelListener(TransportListener channelListener)
    {
        return transportActorContext.registerListener(channelListener);
    }

    public void removeChannelListener(TransportListener listener)
    {
        transportActorContext.removeListener(listener);
    }

    public CompletableFuture<Void> closeAsync()
    {
        return transportActorContext.onClose()
            .whenComplete((v, t) ->
            {
                serverSocketBinding.close();
                Arrays.asList(transportContext.getActorReferences())
                    .forEach(r -> r.close());
            });
    }

    public CompletableFuture<Void> interruptAllChannels()
    {
        return transportActorContext.interruptAllChannels();
    }

    @Override
    public void close()
    {
        closeAsync().join();
    }

}
