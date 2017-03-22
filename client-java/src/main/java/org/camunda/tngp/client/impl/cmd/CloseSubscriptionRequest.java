package org.camunda.tngp.client.impl.cmd;

public class CloseSubscriptionRequest
{

    protected int topicId;
    protected long subscriberKey;

    public long getSubscriberKey()
    {
        return subscriberKey;
    }

    public void setSubscriberKey(long id)
    {
        this.subscriberKey = id;
    }

    public int getTopicId()
    {
        return topicId;
    }

    public void setTopicId(int topicId)
    {
        this.topicId = topicId;
    }

    public void reset()
    {
        this.subscriberKey = -1L;
        this.topicId = -1;
    }
}
