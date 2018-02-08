package io.zeebe.transport.impl;

import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.*;

public class ServerInputSubscriptionImpl implements ServerInputSubscription
{
    protected final Subscription subscription;
    protected final FragmentHandler fragmentHandler;

    public ServerInputSubscriptionImpl(
            ServerOutput output,
            Subscription subscription,
            RemoteAddressList addressList,
            ServerMessageHandler messageHandler,
            ServerRequestHandler requestHandler)
    {
        this.subscription = subscription;
        this.fragmentHandler = new ServerReceiveHandler(output, addressList, messageHandler, requestHandler, null);
    }

    @Override
    public int poll()
    {
        return poll(Integer.MAX_VALUE);
    }

    @Override
    public int poll(int maxCount)
    {
        return subscription.poll(fragmentHandler, maxCount);
    }
}