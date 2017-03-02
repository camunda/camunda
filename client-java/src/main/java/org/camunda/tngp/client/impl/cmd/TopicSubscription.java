package org.camunda.tngp.client.impl.cmd;

public class TopicSubscription
{

    protected long id;
    protected int topicId;
    protected long startPosition;

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

    public void reset()
    {
        this.id = -1L;
        this.topicId = -1;
        this.startPosition = -1L;
    }
}
