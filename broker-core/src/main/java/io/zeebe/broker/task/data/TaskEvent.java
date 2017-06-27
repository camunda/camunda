package io.zeebe.broker.task.data;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.BinaryProperty;
import io.zeebe.broker.util.msgpack.property.EnumProperty;
import io.zeebe.broker.util.msgpack.property.IntegerProperty;
import io.zeebe.broker.util.msgpack.property.LongProperty;
import io.zeebe.broker.util.msgpack.property.ObjectProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.Protocol;

public class TaskEvent extends UnpackedObject
{
    protected static final DirectBuffer NO_PAYLOAD = new UnsafeBuffer(MsgPackHelper.NIL);

    private final EnumProperty<TaskEventType> eventTypeProp = new EnumProperty<>("eventType", TaskEventType.class);
    private final LongProperty lockTimeProp = new LongProperty("lockTime", Protocol.INSTANT_NULL_VALUE);
    private final StringProperty lockOwnerProp = new StringProperty("lockOwner", "");
    private final IntegerProperty retriesProp = new IntegerProperty("retries", -1);
    private final StringProperty typeProp = new StringProperty("type");
    private final ObjectProperty<TaskHeaders> headersProp = new ObjectProperty<>("headers", new TaskHeaders());
    private final BinaryProperty payloadProp = new BinaryProperty("payload", NO_PAYLOAD);

    public TaskEvent()
    {
        this.declareProperty(eventTypeProp)
            .declareProperty(lockTimeProp)
            .declareProperty(lockOwnerProp)
            .declareProperty(retriesProp)
            .declareProperty(typeProp)
            .declareProperty(headersProp)
            .declareProperty(payloadProp);
    }

    public TaskEventType getEventType()
    {
        return eventTypeProp.getValue();
    }

    public TaskEvent setEventType(TaskEventType type)
    {
        eventTypeProp.setValue(type);
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

    public DirectBuffer getLockOwner()
    {
        return lockOwnerProp.getValue();
    }

    public TaskEvent setLockOwner(DirectBuffer lockOwer)
    {
        return setLockOwner(lockOwer, 0, lockOwer.capacity());
    }

    public TaskEvent setLockOwner(DirectBuffer lockOwer, int offset, int length)
    {
        lockOwnerProp.setValue(lockOwer, offset, length);
        return this;
    }

    public int getRetries()
    {
        return retriesProp.getValue();
    }

    public TaskEvent setRetries(int retries)
    {
        retriesProp.setValue(retries);
        return this;
    }

    public DirectBuffer getType()
    {
        return typeProp.getValue();
    }

    public TaskEvent setType(DirectBuffer buf)
    {
        return setType(buf, 0, buf.capacity());
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

    public TaskHeaders headers()
    {
        return headersProp.getValue();
    }

}

