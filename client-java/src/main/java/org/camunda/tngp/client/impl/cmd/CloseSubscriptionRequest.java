package org.camunda.tngp.client.impl.cmd;

public class CloseSubscriptionRequest
{

    protected long subscriptionId;

    public long getSubscriptionId()
    {
        return subscriptionId;
    }

    public void setSubscriptionId(long id)
    {
        this.subscriptionId = id;
    }

    public void reset()
    {
        this.subscriptionId = -1L;
    }
}
