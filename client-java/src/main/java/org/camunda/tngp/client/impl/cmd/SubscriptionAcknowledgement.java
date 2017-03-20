package org.camunda.tngp.client.impl.cmd;

public class SubscriptionAcknowledgement
{

    protected String subscriptionName;
    protected final SubscriptionEventType event = SubscriptionEventType.ACKNOWLEDGE;
    protected long ackPosition;

    public String getSubscriptionName()
    {
        return subscriptionName;
    }

    public void setSubscriptionName(String subscriptionName)
    {
        this.subscriptionName = subscriptionName;
    }

    public long getAckPosition()
    {
        return ackPosition;
    }

    public void setAckPosition(long ackPosition)
    {
        this.ackPosition = ackPosition;
    }

    public SubscriptionEventType getEvent()
    {
        return event;
    }

    public void reset()
    {
        this.subscriptionName = null;
        this.ackPosition = -1L;
    }
}
