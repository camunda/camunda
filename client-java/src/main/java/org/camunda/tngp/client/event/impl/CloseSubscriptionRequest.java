package org.camunda.tngp.client.event.impl;

public class CloseSubscriptionRequest
{

    protected String topicName;
    protected int partitionId;
    protected long subscriberKey;

    public long getSubscriberKey()
    {
        return subscriberKey;
    }

    public void setSubscriberKey(final long subscriberKey)
    {
        this.subscriberKey = subscriberKey;
    }

    public String getTopicName()
    {
        return topicName;
    }

    public void setTopicName(final String topicName)
    {
        this.topicName = topicName;
    }

    public int getPartitionId()
    {
        return partitionId;
    }

    public void setPartitionId(final int partitionId)
    {
        this.partitionId = partitionId;
    }

    public void reset()
    {
        this.subscriberKey = -1L;
        this.topicName = null;
        this.partitionId = -1;
    }
}
