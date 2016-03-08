package net.long_running.transport.impl;

import java.nio.channels.SocketChannel;

import net.long_running.transport.ChannelErrorHandler;
import net.long_running.transport.ChannelFrameHandler;
import net.long_running.transport.ServerChannel;

public class ServerChannelImpl extends BaseChannelImpl implements ServerChannel
{

    public ServerChannelImpl(
            final TransportContext transportContext,
            final SocketChannel media,
            final ChannelFrameHandler frameHandler,
            final ChannelErrorHandler errorHandler)
    {
        super(transportContext, frameHandler, errorHandler);
        this.media = media;
        this.state = State.CONNECTED;
    }

}
