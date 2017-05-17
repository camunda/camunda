package org.camunda.tngp.transport;

import org.agrona.DirectBuffer;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

/**
 * A simple implementation of {@link TransportChannelHandler} discarding errors and
 * handling {@link #onChannelReceive(TransportChannel, DirectBuffer, int, int)} with a
 * receive buffer.
 *
 */
public class ReceiveBufferChannelHandler implements TransportChannelHandler
{
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
        System.err.println("send error on channel " + transportChannel);
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
