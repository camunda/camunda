package io.zeebe.transport.impl;

import io.zeebe.transport.Loggers;
import org.agrona.DirectBuffer;
import io.zeebe.transport.Channel;
import io.zeebe.transport.spi.TransportChannelHandler;
import org.slf4j.Logger;

public class DefaultChannelHandler implements TransportChannelHandler
{
    public static final Logger LOG = Loggers.TRANSPORT_LOGGER;

    @Override
    public void onChannelOpened(Channel transportChannel)
    {
        // no-op
    }

    @Override
    public void onChannelClosed(Channel transportChannel)
    {
        // no-op
    }

    @Override
    public void onChannelSendError(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        LOG.error("onChannelSendError() on channel {} ignored by {}", transportChannel, DefaultChannelHandler.class.getName());
    }

    @Override
    public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        LOG.error("received and dropped {} bytes on channel {} in {}", length, transportChannel, DefaultChannelHandler.class.getName());
        return true;
    }

    @Override
    public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        LOG.error("received and dropped control frame on channel {} in {}", transportChannel, DefaultChannelHandler.class.getName());
        return true;
    }

}
