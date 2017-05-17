package org.camunda.tngp.transport.impl;

import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.Channel;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

public class DefaultChannelHandler implements TransportChannelHandler
{

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
        System.err.println("onChannelSendError() on channel " + transportChannel + " ignored by " + DefaultChannelHandler.class.getName());
    }

    @Override
    public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        System.err.println("received and dropped " + length + " bytes on channel " + transportChannel + " in " + DefaultChannelHandler.class.getName());
        return true;
    }

    @Override
    public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        System.err.println("received and dropped control frame on channel " + transportChannel + " in " + DefaultChannelHandler.class.getName());
        return true;
    }

}
