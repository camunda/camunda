package org.camunda.tngp.broker.event.processor;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.EnumProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;

public class TopicSubscriptionEvent extends UnpackedObject
{
    protected StringProperty subscriptionNameProp = new StringProperty("subscriptionName");
    protected EnumProperty<TopicSubscriptionEventType> eventProp = new EnumProperty<>("event", TopicSubscriptionEventType.class);
    protected LongProperty ackPositionProp = new LongProperty("ackPosition");

    public TopicSubscriptionEvent()
    {
        declareProperty(subscriptionNameProp)
            .declareProperty(eventProp)
            .declareProperty(ackPositionProp);
    }

    public TopicSubscriptionEvent subscriptionName(DirectBuffer nameBuffer, int offset, int length)
    {
        this.subscriptionNameProp.setValue(nameBuffer, offset, length);
        return this;
    }

    public DirectBuffer getSubscriptionName()
    {
        return subscriptionNameProp.getValue();
    }

    public long getAckPosition()
    {
        return ackPositionProp.getValue();
    }

    public void ackPosition(long ackPosition)
    {
        this.ackPositionProp.setValue(ackPosition);
    }

    public TopicSubscriptionEventType getEvent()
    {
        return eventProp.getValue();
    }

    public TopicSubscriptionEvent event(TopicSubscriptionEventType event)
    {
        this.eventProp.setValue(event);
        return this;
    }

}
