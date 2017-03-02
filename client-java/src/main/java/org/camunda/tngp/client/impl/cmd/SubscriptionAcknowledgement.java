package org.camunda.tngp.client.impl.cmd;

public class SubscriptionAcknowledgement
{

    protected long subscriptionId;
    protected long acknowledgedPosition;

    public long getSubscriptionId()
    {
        return subscriptionId;
    }
    public void setSubscriptionId(long subscriptionId)
    {
        this.subscriptionId = subscriptionId;
    }
    public long getAcknowledgedPosition()
    {
        return acknowledgedPosition;
    }
    public void setAcknowledgedPosition(long acknowledgedPosition)
    {
        this.acknowledgedPosition = acknowledgedPosition;
    }

    public void reset()
    {
        this.subscriptionId = -1L;
        this.acknowledgedPosition = -1L;
    }
}
