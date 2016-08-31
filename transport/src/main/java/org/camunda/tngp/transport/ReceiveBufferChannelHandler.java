package org.camunda.tngp.transport;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

import org.agrona.DirectBuffer;

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
    public void onChannelOpened(TransportChannel transportChannel)
    {
        // ignore
    }

    @Override
    public void onChannelClosed(TransportChannel transportChannel)
    {
        // ignore
    }

    @Override
    public void onChannelSendError(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        System.err.println("send error on channel " + transportChannel);
    }

    @Override
    public boolean onChannelReceive(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        long offerPosition = -1;

        do
        {
            offerPosition = receiveBuffer.offer(buffer, offset, length, transportChannel.getId());
        }
        while (offerPosition == -2);

        return offerPosition >= 0;
    }

    @Override
    public void onControlFrame(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        // drop
    }

}
