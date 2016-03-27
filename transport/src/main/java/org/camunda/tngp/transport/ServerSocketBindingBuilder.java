package org.camunda.tngp.transport;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.transport.impl.ServerSocketBindingImpl;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.impl.agent.TransportConductorCmd;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class ServerSocketBindingBuilder
{
    protected final TransportContext transportContext;

    protected final InetSocketAddress bindAddress;

    protected ManyToOneConcurrentArrayQueue<TransportConductorCmd> conductorCmdQueue;

    protected TransportChannelHandler channelHandler;

    public ServerSocketBindingBuilder(TransportContext transportContext, InetSocketAddress bindAddress)
    {
        this.transportContext = transportContext;
        this.bindAddress = bindAddress;
        this.conductorCmdQueue = transportContext.getConductorCmdQueue();
    }

    public ServerSocketBindingBuilder transportChannelHandler(TransportChannelHandler channelHandler)
    {
        this.channelHandler = channelHandler;
        return this;
    }

    public ServerSocketBinding bind()
    {
        return bindAsync().join();
    }

    public CompletableFuture<ServerSocketBinding> bindAsync()
    {
        final CompletableFuture<ServerSocketBinding> bindFuture = new CompletableFuture<>();

        final ServerSocketBindingImpl serverSocketBindingImpl = new ServerSocketBindingImpl(
                transportContext,
                bindAddress,
                channelHandler);

        conductorCmdQueue.add((cc) ->
        {
           cc.doBindServerSocket(serverSocketBindingImpl, bindFuture);
        });

        return bindFuture;
    }

}
