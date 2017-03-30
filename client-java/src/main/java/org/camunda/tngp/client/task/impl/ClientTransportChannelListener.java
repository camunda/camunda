package org.camunda.tngp.client.task.impl;

import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

/**
 * Triggers operations that must be performed on various channel events
 */
public class ClientTransportChannelListener implements TransportChannelHandler
{

    protected final SubscriptionManager subscriptionManager;

    public ClientTransportChannelListener(SubscriptionManager subscriptionManager)
    {
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public void onChannelOpened(TransportChannel transportChannel)
    {
    }

    @Override
    public void onChannelClosed(TransportChannel transportChannel)
    {
        subscriptionManager.onChannelClosed(transportChannel.getId());
    }

    @Override
    public void onChannelSendError(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
    }

    @Override
    public boolean onChannelReceive(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        return false;
    }

    @Override
    public void onControlFrame(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
    }

}
