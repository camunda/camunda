package io.zeebe.transport.impl;

import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.*;
import io.zeebe.util.sched.channel.ChannelSubscription;
import io.zeebe.util.sched.channel.ConsumableChannel;
import org.agrona.DirectBuffer;

public class ClientInputMessageSubscriptionImpl implements ClientInputMessageSubscription
{
    protected final Subscription subscription;
    protected final FragmentHandler messageHandler;

    public ClientInputMessageSubscriptionImpl(
        Subscription subscription,
        ClientMessageHandler messageHandler,
        ClientOutput output,
        RemoteAddressList remoteAddresses)
    {
        this.subscription = subscription;
        this.messageHandler = new FragmentHandler()
        {
            @Override
            public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
            {
                final RemoteAddress remoteAddress = remoteAddresses.getByStreamId(streamId);
                final boolean success = messageHandler.onMessage(output, remoteAddress, buffer, offset, length);

                return success ? CONSUME_FRAGMENT_RESULT : POSTPONE_FRAGMENT_RESULT;
            }
        };
    }

    @Override
    public int poll()
    {
        return subscription.peekAndConsume(messageHandler, Integer.MAX_VALUE);
    }

    @Override
    public boolean hasAvailable()
    {
        return subscription.hasAvailable();
    }

    @Override
    public void registerConsumer(ChannelSubscription listener)
    {
        subscription.registerConsumer(listener);
    }

    @Override
    public void removeConsumer(ChannelSubscription listener)
    {
        subscription.removeConsumer(listener);
    }

}
