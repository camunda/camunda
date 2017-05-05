package org.camunda.tngp.transport.impl;

import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class DefaultChannelHandler implements TransportChannelHandler
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
        System.err.println("onChannelSendError() on channel " + transportChannel + " ignored by " + DefaultChannelHandler.class.getName());
    }

    @Override
    public boolean onChannelReceive(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        System.err.println("received and dropped " + length + " bytes on channel " + transportChannel + " in " + DefaultChannelHandler.class.getName());
        return true;
    }

    @Override
    public boolean onControlFrame(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        System.err.println("received and dropped control frame on channel " + transportChannel + " in " + DefaultChannelHandler.class.getName());
        return true;
    }

}
