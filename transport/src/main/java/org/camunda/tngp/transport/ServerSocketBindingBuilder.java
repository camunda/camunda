package org.camunda.tngp.transport;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.transport.impl.DefaultChannelHandler;
import org.camunda.tngp.transport.impl.agent.Conductor;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class ServerSocketBindingBuilder
{
    protected final Conductor conductor;
    protected final InetSocketAddress bindAddress;

    protected TransportChannelHandler channelHandler = new DefaultChannelHandler();

    public ServerSocketBindingBuilder(Conductor conductor, InetSocketAddress bindAddress)
    {
        this.conductor = conductor;
        this.bindAddress = bindAddress;
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
        return conductor.bindServerSocketAsync(bindAddress, channelHandler);
    }

}
