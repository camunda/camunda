package org.camunda.tngp.transport;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.transport.impl.ClientChannelImpl;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.requestresponse.client.RequestResponseChannelHandler;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPoolImpl;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

import uk.co.real_logic.agrona.DirectBuffer;

public class ClientChannelBuilder
{
    public final static DefaultClientChannelHandler DEFAULT_HANDLER = new DefaultClientChannelHandler();

    protected final TransportContext transportContext;

    protected final InetSocketAddress remoteAddress;

    protected boolean isProtocolChannel = true;

    protected TransportChannelHandler channelHandler = DEFAULT_HANDLER;

    public ClientChannelBuilder(TransportContext transportContext, InetSocketAddress remoteAddress)
    {
        this.transportContext = transportContext;
        this.remoteAddress = remoteAddress;
    }

    public ClientChannelBuilder transportChannelHandler(TransportChannelHandler channelHandler)
    {
        this.channelHandler = channelHandler;
        return this;
    }

    public ClientChannelBuilder requestResponseChannel(TransportConnectionPool connectionPool)
    {
        return transportChannelHandler(new RequestResponseChannelHandler((TransportConnectionPoolImpl) connectionPool));
    }

    public CompletableFuture<ClientChannel> connectAsync()
    {
        final CompletableFuture<ClientChannel> future = new CompletableFuture<ClientChannel>();

        final ClientChannelImpl channel = new ClientChannelImpl(transportContext, channelHandler, remoteAddress);

        transportContext.getConductorCmdQueue().add((c) ->
        {
            c.doConnectChannel(channel, future);
        });

        return future;
    }

    public ClientChannel connect()
    {
        return connectAsync().join();
    }

    public static class DefaultClientChannelHandler implements TransportChannelHandler
    {

        @Override
        public void onChannelOpened(TransportChannel transportChannel)
        {
            // no-op
        }

        @Override
        public void onChannelClosed(TransportChannel transportChannel)
        {
            // no-op
        }

        @Override
        public void onChannelSendError(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
        {
            System.err.println("onChannelSendError() on channel "+transportChannel+" ignored by "+DefaultClientChannelHandler.class.getName());
        }

        @Override
        public boolean onChannelReceive(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
        {
            System.err.println("received and dropped "+length+" bytes on channel "+transportChannel+" in "+ DefaultClientChannelHandler.class.getName());
            return true;
        }

        @Override
        public void onControlFrame(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
        {
            System.err.println("received and dropped control frame on channel "+transportChannel+" in "+ DefaultClientChannelHandler.class.getName());
        }

    }

}
