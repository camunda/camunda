package org.camunda.tngp.broker.taskqueue.data;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.BinaryProperty;
import org.camunda.tngp.broker.util.msgpack.property.EnumProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;
import org.camunda.tngp.broker.util.msgpack.property.PackedProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;

public class TaskEvent extends UnpackedObject
{
    private final EnumProperty<TaskEventType> eventProp = new EnumProperty<>("event", TaskEventType.class);
    private final LongProperty lockTimeProp = new LongProperty("lockTime");
    private final StringProperty typeProp = new StringProperty("type");
    private final PackedProperty headersProp = new PackedProperty("headers");
    private final BinaryProperty payloadProp = new BinaryProperty("payload");

    public TaskEvent()
    {
        objectValue.declareProperty(eventProp)
            .declareProperty(lockTimeProp)
            .declareProperty(typeProp)
            .declareProperty(headersProp)
            .declareProperty(payloadProp);
    }

    public TaskEventType getEventType()
    {
        return eventProp.getValue();
    }

    public TaskEvent setEventType(TaskEventType type)
    {
        eventProp.setValue(type);
        return this;
    }

    public long getLockTime()
    {
        return lockTimeProp.getValue();
    }

    public TaskEvent setLockTime(long val)
    {
        lockTimeProp.setValue(val);
        return this;
    }

    public DirectBuffer getType()
    {
        return typeProp.getValue();
    }

    public TaskEvent setType(DirectBuffer buf, int offset, int length)
    {
        typeProp.setValue(buf, offset, length);
        return this;
    }

    public DirectBuffer getPayload()
    {
        return payloadProp.getValue();
    }

    public TaskEvent setPayload(DirectBuffer payload)
    {
        payloadProp.setValue(payload);
        return this;
    }

    public TaskEvent setPayload(DirectBuffer payload, int offset, int length)
    {
        payloadProp.setValue(payload, offset, length);
        return this;
    }

}

