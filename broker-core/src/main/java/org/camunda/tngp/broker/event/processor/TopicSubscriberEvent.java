package org.camunda.tngp.broker.event.processor;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.BooleanProperty;
import org.camunda.tngp.broker.util.msgpack.property.EnumProperty;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;

public class TopicSubscriberEvent extends UnpackedObject
{
    // negative value for end of log
    protected LongProperty startPositionProp = new LongProperty("startPosition", -1L);
    protected IntegerProperty prefetchCapacityProp = new IntegerProperty("prefetchCapacity", -1);
    protected StringProperty nameProp = new StringProperty("name");

    // true if startPosition should override any previously acknowledged position
    protected BooleanProperty forceStartProp = new BooleanProperty("forceStart", false);
    protected EnumProperty<TopicSubscriberEventType> eventProp = new EnumProperty<>("event", TopicSubscriberEventType.class);

    public TopicSubscriberEvent()
    {
        this
            .declareProperty(eventProp)
            .declareProperty(startPositionProp)
            .declareProperty(nameProp)
            .declareProperty(prefetchCapacityProp)
            .declareProperty(forceStartProp);
    }

    public TopicSubscriberEvent startPosition(long startPosition)
    {
        this.startPositionProp.setValue(startPosition);
        return this;
    }

    public long getStartPosition()
    {
        return startPositionProp.getValue();
    }

    public TopicSubscriberEvent prefetchCapacity(int prefetchCapacity)
    {
        this.prefetchCapacityProp.setValue(prefetchCapacity);
        return this;
    }

    public int getPrefetchCapacity()
    {
        return prefetchCapacityProp.getValue();
    }

    public String getNameAsString()
    {
        final DirectBuffer stringBuffer = nameProp.getValue();
        return stringBuffer.getStringWithoutLengthUtf8(0, stringBuffer.capacity());
    }

    public DirectBuffer getName()
    {
        return nameProp.getValue();
    }

    public TopicSubscriberEvent setName(String name)
    {
        nameProp.setValue(name);
        return this;
    }

    public boolean getForceStart()
    {
        return forceStartProp.getValue();
    }

    public TopicSubscriberEventType getEvent()
    {
        return eventProp.getValue();
    }

    public TopicSubscriberEvent setEvent(TopicSubscriberEventType event)
    {
        this.eventProp.setValue(event);
        return this;
    }

}
