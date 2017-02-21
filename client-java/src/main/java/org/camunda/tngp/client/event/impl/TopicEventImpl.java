package org.camunda.tngp.client.event.impl;

import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.TopicEvent;
import org.camunda.tngp.client.event.TopicEventType;
import org.camunda.tngp.client.task.impl.MsgPackField;

public class TopicEventImpl implements TopicEvent, EventMetadata
{

    protected final long key;
    protected final long position;
    protected final TopicEventType eventType;
    protected final MsgPackField content;

    public TopicEventImpl(long key, long position, TopicEventType eventType, byte[] rawContent)
    {
        this.position = position;
        this.key = key;
        this.eventType = eventType;
        this.content = new MsgPackField();
        this.content.setMsgPack(rawContent);
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

}
