package org.camunda.tngp.broker.event.processor;

import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;

public class TopicSubscription extends UnpackedObject
{
    protected LongProperty idProp = new LongProperty("id", -1L);
    protected IntegerProperty topicIdProp = new IntegerProperty("topicId");
    // negative value for end of log
    protected LongProperty startPositionProp = new LongProperty("startPosition", -1L);

    protected int channelId;

    public TopicSubscription()
    {
        this.declareProperty(idProp)
            .declareProperty(topicIdProp)
            .declareProperty(startPositionProp);
    }

    public long getId()
    {
        return idProp.getValue();
    }

    public TopicSubscription setId(long id)
    {
        this.idProp.setValue(id);
        return this;
    }

    public int getTopicId()
    {
        return topicIdProp.getValue();
    }

    public TopicSubscription setTopicId(int topicId)
    {
        this.topicIdProp.setValue(topicId);
        return this;
    }

    public int getChannelId()
    {
        return channelId;
    }

    public TopicSubscription setChannelId(int channelId)
    {
        this.channelId = channelId;
        return this;
    }

    public TopicSubscription startPosition(long startPosition)
    {
        this.startPositionProp.setValue(startPosition);
        return this;
    }

    public long getStartPosition()
    {
        return startPositionProp.getValue();
    }

}
