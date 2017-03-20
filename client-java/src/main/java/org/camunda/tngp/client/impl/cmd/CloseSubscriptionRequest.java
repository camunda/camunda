package org.camunda.tngp.client.impl.cmd;

public class CloseSubscriptionRequest
{

    protected int topicId;
    protected long subscriptionId;

    public long getSubscriptionId()
    {
        return subscriptionId;
    }

    public void setSubscriptionId(long id)
    {
        this.subscriptionId = id;
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
        this.subscriptionId = -1L;
        this.topicId = -1;
    }
}
