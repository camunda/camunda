package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.TopicEvent;
import org.camunda.tngp.client.event.TopicEventType;
import org.camunda.tngp.client.task.impl.subscription.MsgPackField;

public class TopicEventImpl implements TopicEvent, EventMetadata
{

    protected final String topicName;
    protected final int partitionId;
    protected final long key;
    protected final long position;
    protected final TopicEventType eventType;
    protected final MsgPackField content;

    public TopicEventImpl(final String topicName, final int partitionId, final long key, final long position, final TopicEventType eventType, final byte[] rawContent)
    {
        this.topicName = topicName;
        this.partitionId = partitionId;
        this.position = position;
        this.key = key;
        this.eventType = eventType;
        this.content = new MsgPackField();
        this.content.setMsgPack(rawContent);
    }

    public String getTopicName()
    {
        return topicName;
    }

    @Override
    public int getPartitionId()
    {
        return partitionId;
    }

    @Override
    public long getEventKey()
    {
        return key;
    }

    @Override
    public long getEventPosition()
    {
        return position;
    }

    @Override
    public String getJson()
    {
        return content.getAsJson();
    }

    public byte[] getAsMsgPack()
    {
        return content.getMsgPack();
    }

    @Override
    public TopicEventType getEventType()
    {
        return eventType;
    }

    @Override
    public String toString()
    {
        return "TopicEventImpl [topicName=" + topicName + ", partitionId=" + partitionId + ", key=" +
                key + ", position=" + position + ", eventType=" + eventType + ", content=" + content.getAsJson() + "]";
    }

}
