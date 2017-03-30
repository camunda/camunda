package org.camunda.tngp.client.task.impl;

public class EventSubscriptionCreationResult
{
    protected final long subscriptionId;
    protected final int receiveChannel;

    public EventSubscriptionCreationResult(long subscriptionId, int receiveChannel)
    {
        this.subscriptionId = subscriptionId;
        this.receiveChannel = receiveChannel;
    }

    public int getReceiveChannelId()
    {
        return receiveChannel;
    }

    public long getSubscriptionId()
    {
        return subscriptionId;
    }

}
