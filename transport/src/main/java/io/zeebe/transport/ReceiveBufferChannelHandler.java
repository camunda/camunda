package io.zeebe.transport;

import org.agrona.DirectBuffer;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.spi.TransportChannelHandler;
import org.slf4j.Logger;

/**
 * A simple implementation of {@link TransportChannelHandler} discarding errors and
 * handling {@link #onChannelReceive(TransportChannel, DirectBuffer, int, int)} with a
 * receive buffer.
 *
 */
public class ReceiveBufferChannelHandler implements TransportChannelHandler
{
    public static final Logger LOG = Loggers.TRANSPORT_LOGGER;

    protected Dispatcher receiveBuffer;

    public ReceiveBufferChannelHandler(Dispatcher receiveBuffer)
    {
        this.receiveBuffer = receiveBuffer;
    }

    @Override
    public void onChannelOpened(Channel transportChannel)
    {
        // ignore
    }

    @Override
    public void onChannelClosed(Channel transportChannel)
    {
        // ignore
    }

    @Override
    public void onChannelSendError(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        LOG.error("Send error on channel {}", transportChannel);
    }

    @Override
    public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        long offerPosition = -1;

        do
        {
            offerPosition = receiveBuffer.offer(buffer, offset, length, transportChannel.getStreamId());
        }
        while (offerPosition == -2);

        return offerPosition >= 0;
    }

    @Override
    public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        // drop
        return true;
    }

}
