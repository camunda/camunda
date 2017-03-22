package org.camunda.tngp.client.impl.cmd;

public class TopicSubscriberEvent
{

    protected SubscriberEventType event;
    protected long startPosition;
    protected String name;
    protected int prefetchCapacity;

    public TopicSubscriberEvent()
    {
        reset();
    }

    public long getStartPosition()
    {
        return startPosition;
    }

    public void setStartPosition(long startPosition)
    {
        this.startPosition = startPosition;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public void setPrefetchCapacity(int prefetchCapacity)
    {
        this.prefetchCapacity = prefetchCapacity;
    }

    public int getPrefetchCapacity()
    {
        return prefetchCapacity;
    }

    public void setEvent(SubscriberEventType event)
    {
        this.event = event;
    }

    public SubscriberEventType getEvent()
    {
        return event;
    }

    public void reset()
    {
        this.startPosition = -1L;
        this.prefetchCapacity = -1;
        this.event = null;
        this.name = null;
    }
}
