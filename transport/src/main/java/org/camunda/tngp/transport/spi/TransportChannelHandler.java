package org.camunda.tngp.transport.spi;

import org.camunda.tngp.transport.TransportChannel;

import org.agrona.DirectBuffer;

public interface TransportChannelHandler
{

    // invoked in the conductor thread
    void onChannelOpened(TransportChannel transportChannel);

    // invoked in the conductor thread
    void onChannelClosed(TransportChannel transportChannel);

    // invoked in the sender thread
    void onChannelSendError(
            TransportChannel transportChannel,
            DirectBuffer buffer,
            int offset,
            int length);

    // invoked in the receiver thread
    boolean onChannelReceive(
            TransportChannel transportChannel,
            DirectBuffer buffer,
            int offset,
            int length);

    // invoked in the receiver thread
    void onControlFrame(
            TransportChannel transportChannel,
            DirectBuffer buffer,
            int offset,
            int length);

}
