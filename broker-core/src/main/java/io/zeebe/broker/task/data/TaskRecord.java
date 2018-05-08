/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.task.data;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.*;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.Protocol;

public class TaskRecord extends UnpackedObject
{
    protected static final DirectBuffer NO_PAYLOAD = new UnsafeBuffer(MsgPackHelper.NIL);
    protected static final DirectBuffer NO_HEADERS = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);

    private final LongProperty lockTimeProp = new LongProperty("lockTime", Protocol.INSTANT_NULL_VALUE);
    private final StringProperty lockOwnerProp = new StringProperty("lockOwner", "");
    private final IntegerProperty retriesProp = new IntegerProperty("retries", -1);
    private final StringProperty typeProp = new StringProperty("type");
    private final ObjectProperty<TaskHeaders> headersProp = new ObjectProperty<>("headers", new TaskHeaders());
    private final PackedProperty customHeadersProp = new PackedProperty("customHeaders", NO_HEADERS);
    private final BinaryProperty payloadProp = new BinaryProperty("payload", NO_PAYLOAD);

    public TaskRecord()
    {
        this
            .declareProperty(lockTimeProp)
            .declareProperty(lockOwnerProp)
            .declareProperty(retriesProp)
            .declareProperty(typeProp)
            .declareProperty(headersProp)
            .declareProperty(customHeadersProp)
            .declareProperty(payloadProp);
    }

    public long getLockTime()
    {
        return lockTimeProp.getValue();
    }

    public TaskRecord setLockTime(long val)
    {
        lockTimeProp.setValue(val);
        return this;
    }

    public DirectBuffer getLockOwner()
    {
        return lockOwnerProp.getValue();
    }

    public TaskRecord setLockOwner(DirectBuffer lockOwer)
    {
        return setLockOwner(lockOwer, 0, lockOwer.capacity());
    }

    public TaskRecord setLockOwner(DirectBuffer lockOwer, int offset, int length)
    {
        lockOwnerProp.setValue(lockOwer, offset, length);
        return this;
    }

    public int getRetries()
    {
        return retriesProp.getValue();
    }

    public TaskRecord setRetries(int retries)
    {
        retriesProp.setValue(retries);
        return this;
    }

    public DirectBuffer getType()
    {
        return typeProp.getValue();
    }

    public TaskRecord setType(DirectBuffer buf)
    {
        return setType(buf, 0, buf.capacity());
    }

    public TaskRecord setType(DirectBuffer buf, int offset, int length)
    {
        typeProp.setValue(buf, offset, length);
        return this;
    }

    public DirectBuffer getPayload()
    {
        return payloadProp.getValue();
    }

    public TaskRecord setPayload(DirectBuffer payload)
    {
        payloadProp.setValue(payload);
        return this;
    }

    public TaskRecord setPayload(DirectBuffer payload, int offset, int length)
    {
        payloadProp.setValue(payload, offset, length);
        return this;
    }

    public TaskHeaders headers()
    {
        return headersProp.getValue();
    }

    public void setCustomHeaders(DirectBuffer buffer, int offset, int length)
    {
        customHeadersProp.setValue(buffer, offset, length);
    }

    public void setCustomHeaders(DirectBuffer buffer)
    {
        customHeadersProp.setValue(buffer, 0, buffer.capacity());
    }

    public DirectBuffer getCustomHeaders()
    {
        return customHeadersProp.getValue();
    }
}

