package io.zeebe.broker.event.processor;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.EnumProperty;
import io.zeebe.broker.util.msgpack.property.LongProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;

public class TopicSubscriptionEvent extends UnpackedObject
{
    protected StringProperty nameProp = new StringProperty("name");
    protected EnumProperty<TopicSubscriptionEventType> eventProp = new EnumProperty<>("eventType", TopicSubscriptionEventType.class);
    protected LongProperty ackPositionProp = new LongProperty("ackPosition");

    public TopicSubscriptionEvent()
    {
        declareProperty(nameProp)
            .declareProperty(eventProp)
            .declareProperty(ackPositionProp);
    }

    public TopicSubscriptionEvent setName(DirectBuffer nameBuffer, int offset, int length)
    {
        this.nameProp.setValue(nameBuffer, offset, length);
        return this;
    }

    public DirectBuffer getName()
    {
        return nameProp.getValue();
    }

    public long getAckPosition()
    {
        return ackPositionProp.getValue();
    }

    public TopicSubscriptionEvent setAckPosition(long ackPosition)
    {
        this.ackPositionProp.setValue(ackPosition);
        return this;
    }

    public TopicSubscriptionEventType getEventType()
    {
        return eventProp.getValue();
    }

    public TopicSubscriptionEvent setEventType(TopicSubscriptionEventType event)
    {
        this.eventProp.setValue(event);
        return this;
    }

}
