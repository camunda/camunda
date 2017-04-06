package org.camunda.tngp.client.impl.cmd;

public class TopicSubscriberEvent
{

    protected SubscriberEventType eventType;
    protected long startPosition;
    protected String name;
    protected int prefetchCapacity;
    protected boolean forceStart;

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

    public void setEventType(SubscriberEventType event)
    {
        this.eventType = event;
    }

    public SubscriberEventType getEventType()
    {
        return eventType;
    }

    public boolean isForceStart()
    {
        return forceStart;
    }

    public void setForceStart(boolean forceStart)
    {
        this.forceStart = forceStart;
    }

    public void reset()
    {
        this.startPosition = -1L;
        this.prefetchCapacity = -1;
        this.eventType = null;
        this.name = null;
        this.forceStart = false;
    }
}
