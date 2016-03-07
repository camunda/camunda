package net.long_running.transport.impl;

import java.nio.channels.SocketChannel;

import net.long_running.transport.ChannelFrameHandler;
import net.long_running.transport.ServerChannel;

public class ServerChannelImpl extends BaseChannelImpl implements ServerChannel
{

    public ServerChannelImpl(
            final ChannelFrameHandler channelReader,
            final TransportContext transportContext,
            final SocketChannel media)
    {
        super(channelReader, transportContext);
        this.media = media;
        this.state = State.CONNECTED;
    }

    @Override
    public void close()
    {
        // TODO
    }

}
