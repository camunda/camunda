package org.camunda.tngp.client.impl.cmd;

public class TopicSubscription
{

    protected long id;
    protected int topicId;
    protected long startPosition;
    protected String name;
    protected int prefetchCapacity;

    public TopicSubscription()
    {
        reset();
    }

    public long getId()
    {
        return id;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public int getTopicId()
    {
        return topicId;
    }

    public void setTopicId(int topicId)
    {
        this.topicId = topicId;
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

    public void reset()
    {
        this.id = -1L;
        this.topicId = -1;
        this.startPosition = -1L;
        this.prefetchCapacity = -1;
    }
}
