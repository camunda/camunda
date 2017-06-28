package org.camunda.tngp.client.task.impl.subscription;

import org.camunda.tngp.client.impl.cmd.ChannelAwareResponseResult;
import org.camunda.tngp.transport.Channel;

public class EventSubscriptionCreationResult implements ChannelAwareResponseResult
{
    protected final long subscriberKey;
    protected Channel receiveChannel;

    public EventSubscriptionCreationResult(final long subscriberKey)
    {
        this.subscriberKey = subscriberKey;
    }

    public Channel getReceiveChannel()
    {
        return receiveChannel;
    }

    public long getSubscriberKey()
    {
        return subscriberKey;
    }

    @Override
    public void setChannel(Channel channel)
    {
        this.receiveChannel = channel;
    }

}
