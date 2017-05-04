package org.camunda.tngp.transport;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.transport.impl.ClientChannelImpl;
import org.camunda.tngp.transport.impl.CompositeChannelHandler;
import org.camunda.tngp.transport.impl.DefaultChannelHandler;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.RequestResponseChannelHandler;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPoolImpl;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class ClientChannelBuilder
{
    public static final DefaultChannelHandler DEFAULT_HANDLER = new DefaultChannelHandler();

    protected final TransportContext transportContext;

    protected final InetSocketAddress remoteAddress;

    protected boolean isProtocolChannel = true;

    protected CompositeChannelHandler channelHandler = new CompositeChannelHandler();

    public ClientChannelBuilder(TransportContext transportContext, InetSocketAddress remoteAddress)
    {
        this.transportContext = transportContext;
        this.remoteAddress = remoteAddress;
    }

    public ClientChannelBuilder transportChannelHandler(short protocolId, TransportChannelHandler channelHandler)
    {
        this.channelHandler.addHandler(protocolId, channelHandler);
        return this;
    }

    public ClientChannelBuilder requestResponseProtocol(TransportConnectionPool connectionPool)
    {

        return transportChannelHandler(Protocols.REQUEST_RESPONSE,
                new RequestResponseChannelHandler((TransportConnectionPoolImpl) connectionPool));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public CompletableFuture<ClientChannel> connectAsync()
    {
        final CompletableFuture<TransportChannel> future = new CompletableFuture<>();

        final ClientChannelImpl channel = new ClientChannelImpl(transportContext, channelHandler, remoteAddress);

        transportContext.getConductorCmdQueue().add((c) ->
        {
            c.doConnectChannel(channel, future);
        });

        return (CompletableFuture) future;
    }

    public ClientChannel connect()
    {
        return connectAsync().join();
    }

}
