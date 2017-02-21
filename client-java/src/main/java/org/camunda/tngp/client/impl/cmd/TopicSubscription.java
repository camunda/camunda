package org.camunda.tngp.client.impl.cmd;

public class TopicSubscription
{

    protected long id;
    protected int topicId;

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

    public void reset()
    {
        this.id = -1L;
        this.topicId = -1;
    }
}
