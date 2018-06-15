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
package io.zeebe.broker.job.data;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.*;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.Protocol;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class JobRecord extends UnpackedObject
{
    protected static final DirectBuffer NO_HEADERS = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);

    private final LongProperty deadlineProp = new LongProperty("deadline", Protocol.INSTANT_NULL_VALUE);
    private final StringProperty workerProp = new StringProperty("worker", "");
    private final IntegerProperty retriesProp = new IntegerProperty("retries", -1);
    private final StringProperty typeProp = new StringProperty("type");
    private final ObjectProperty<JobHeaders> headersProp = new ObjectProperty<>("headers", new JobHeaders());
    private final PackedProperty customHeadersProp = new PackedProperty("customHeaders", NO_HEADERS);
    private final DocumentProperty payloadProp = new DocumentProperty("payload");

    public JobRecord()
    {
        this
            .declareProperty(deadlineProp)
            .declareProperty(workerProp)
            .declareProperty(retriesProp)
            .declareProperty(typeProp)
            .declareProperty(headersProp)
            .declareProperty(customHeadersProp)
            .declareProperty(payloadProp);
    }

    public long getDeadline()
    {
        return deadlineProp.getValue();
    }

    public JobRecord setDeadline(long val)
    {
        deadlineProp.setValue(val);
        return this;
    }

    public DirectBuffer getWorker()
    {
        return workerProp.getValue();
    }

    public JobRecord setWorker(DirectBuffer worker)
    {
        return setWorker(worker, 0, worker.capacity());
    }

    public JobRecord setWorker(DirectBuffer worker, int offset, int length)
    {
        workerProp.setValue(worker, offset, length);
        return this;
    }

    public int getRetries()
    {
        return retriesProp.getValue();
    }

    public JobRecord setRetries(int retries)
    {
        retriesProp.setValue(retries);
        return this;
    }

    public DirectBuffer getType()
    {
        return typeProp.getValue();
    }

    public JobRecord setType(DirectBuffer buf)
    {
        return setType(buf, 0, buf.capacity());
    }

    public JobRecord setType(DirectBuffer buf, int offset, int length)
    {
        typeProp.setValue(buf, offset, length);
        return this;
    }

    public DirectBuffer getPayload()
    {
        return payloadProp.getValue();
    }

    public JobRecord setPayload(DirectBuffer payload)
    {
        payloadProp.setValue(payload);

        return this;
    }

    public JobHeaders headers()
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

