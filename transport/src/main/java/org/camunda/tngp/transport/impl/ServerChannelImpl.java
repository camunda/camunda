package org.camunda.tngp.transport.impl;

import java.nio.channels.SocketChannel;

import org.camunda.tngp.transport.ServerChannel;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class ServerChannelImpl extends TransportChannelImpl implements ServerChannel
{

    protected final ServerSocketBindingImpl serverSocketBinding;

    public ServerChannelImpl(
            final TransportContext transportContext,
            final ServerSocketBindingImpl serverSocketBinding,
            final SocketChannel media,
            final TransportChannelHandler channelHandler)
    {
        super(transportContext, channelHandler);
        this.serverSocketBinding = serverSocketBinding;
        this.media = media;

        STATE_FIELD.set(this, STATE_CONNECTED);
    }

    public ServerSocketBindingImpl getServerSocketBinding()
    {
        return serverSocketBinding;
    }

}
