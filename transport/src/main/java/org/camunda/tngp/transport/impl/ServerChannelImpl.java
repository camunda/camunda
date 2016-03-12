package org.camunda.tngp.transport.impl;

import java.nio.channels.SocketChannel;

import org.camunda.tngp.transport.ChannelErrorHandler;
import org.camunda.tngp.transport.ServerChannel;

public class ServerChannelImpl extends BaseChannelImpl implements ServerChannel
{

    public ServerChannelImpl(
            final TransportContext transportContext,
            final SocketChannel media,
            final ChannelErrorHandler errorHandler)
    {
        super(transportContext, errorHandler);
        this.media = media;
        this.state = State.CONNECTED;
    }

}
