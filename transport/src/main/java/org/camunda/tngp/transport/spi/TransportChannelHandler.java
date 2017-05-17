package org.camunda.tngp.transport.spi;

import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.Channel;

public interface TransportChannelHandler
{

    // invoked in the conductor thread
    void onChannelOpened(Channel transportChannel);

    // invoked in the conductor thread
    void onChannelClosed(Channel transportChannel);

    // invoked in the receiver thread
    default void onChannelKeepAlive(Channel channel)
    {
        // ignore
    }

    // invoked in the sender thread
    void onChannelSendError(
            Channel transportChannel,
            DirectBuffer buffer,
            int offset,
            int length);

    // invoked in the receiver thread
    boolean onChannelReceive(
            Channel transportChannel,
            DirectBuffer buffer,
            int offset,
            int length);

    // invoked in the receiver thread
    boolean onControlFrame(
            Channel transportChannel,
            DirectBuffer buffer,
            int offset,
            int length);

}
