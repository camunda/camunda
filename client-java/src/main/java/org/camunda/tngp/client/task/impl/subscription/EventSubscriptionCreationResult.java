package org.camunda.tngp.client.task.impl.subscription;

public class EventSubscriptionCreationResult
{
    protected final long subscriberKey;
    protected final int receiveChannel;

    public EventSubscriptionCreationResult(long subscriberKey, int receiveChannel)
    {
        this.subscriberKey = subscriberKey;
        this.receiveChannel = receiveChannel;
    }

    public int getReceiveChannelId()
    {
        return receiveChannel;
    }

    public long getSubscriberKey()
    {
        return subscriberKey;
    }

}
