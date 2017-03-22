package org.camunda.tngp.client.impl.cmd;

public class TopicSubscriptionEvent
{

    protected String name;
    protected final SubscriptionEventType event = SubscriptionEventType.ACKNOWLEDGE;
    protected long ackPosition;

    public String getName()
    {
        return name;
    }

    public void setName(String subscriptionName)
    {
        this.name = subscriptionName;
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
        this.name = null;
        this.ackPosition = -1L;
    }
}
