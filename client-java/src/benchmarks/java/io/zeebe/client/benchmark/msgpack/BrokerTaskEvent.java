/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.benchmark.msgpack;

import static io.zeebe.util.StringUtil.getBytes;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.BinaryProperty;
import io.zeebe.broker.util.msgpack.property.EnumProperty;
import io.zeebe.broker.util.msgpack.property.LongProperty;
import io.zeebe.broker.util.msgpack.property.PackedProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;

public class BrokerTaskEvent extends UnpackedObject implements TaskEvent
{
    private final EnumProperty<TaskEventType> eventTypeProp = new EnumProperty<>("eventType", TaskEventType.class);
    private final LongProperty lockTimeProp = new LongProperty("lockTime");
    private final StringProperty typeProp = new StringProperty("type");
    private final PackedProperty headersProp = new PackedProperty("headers");
    private final BinaryProperty payloadProp = new BinaryProperty("payload");

    protected MsgPackWriter writer = new MsgPackWriter();
    protected MsgPackReader reader = new MsgPackReader();

    /**
     * Use this to simulate user interaction, i.e. where a actual java objects are accessed after deserialization
     */
    protected boolean accessValuesOnDeserialization;

    protected UnsafeBuffer tempBuffer = new UnsafeBuffer(0, 0);
    protected byte[] headersArray = new byte[1024];

    public BrokerTaskEvent()
    {
        // default constructor for deserialization
        this(true);
    }

    public BrokerTaskEvent(boolean accessValuesOnDeserialization)
    {
        objectValue.declareProperty(eventTypeProp)
            .declareProperty(lockTimeProp)
            .declareProperty(typeProp)
            .declareProperty(headersProp)
            .declareProperty(payloadProp);

        this.accessValuesOnDeserialization = accessValuesOnDeserialization;
    }

    public TaskEventType getEventType()
    {
        return eventTypeProp.getValue();
    }

    public long getLockTime()
    {
        return lockTimeProp.getValue();
    }

    public void setLockTime(long val)
    {
        lockTimeProp.setValue(val);
    }

    public DirectBuffer getType()
    {
        return typeProp.getValue();
    }

    public DirectBuffer getPayload()
    {
        return payloadProp.getValue();
    }

    @Override
    public void setEventType(TaskEventType event)
    {
        eventTypeProp.setValue(event);
    }

    @Override
    public void setType(String type)
    {
        typeProp.setValue(type);

    }

    @Override
    public void setHeaders(Map<String, String> headers)
    {
        tempBuffer.wrap(headersArray);
        writer.wrap(tempBuffer, 0);
        writer.writeMapHeader(headers.size());
        headers.forEach((k, v) ->
        {
            writer.writeString(new UnsafeBuffer(getBytes(k)));
            writer.writeString(new UnsafeBuffer(getBytes(v)));
        });
        headersProp.setValue(tempBuffer, 0, writer.getOffset());
    }

    @Override
    public void setPayload(byte[] payload)
    {
        tempBuffer.wrap(payload);
        payloadProp.setValue(tempBuffer);

    }

    @Override
    public void wrap(DirectBuffer buff, int offset, int length)
    {
        super.wrap(buff, offset, length);

        if (accessValuesOnDeserialization)
        {
            final DirectBuffer typeValueBuffer = typeProp.getValue();
            readString(typeValueBuffer, 0, typeValueBuffer.capacity());

            final DirectBuffer headersBuffer = headersProp.getValue();
            reader.wrap(headersBuffer, 0, headersBuffer.capacity());
            final int numHeaders = reader.readMapHeader();

            final Map<String, String> headers = new HashMap<>();
            for (int i = 0; i < numHeaders; i++)
            {
                final int keyLength = reader.readStringLength();
                final String key = readString(buff, reader.getOffset(), keyLength);
                reader.skipBytes(keyLength);
                final int valLength = reader.readStringLength();
                final String value = readString(buff, reader.getOffset(), valLength);
                reader.skipBytes(valLength);
                headers.put(key, value);
            }

            final DirectBuffer payloadBuffer = payloadProp.getValue();
            payloadBuffer.getBytes(0, new byte[payloadBuffer.capacity()]);
        }
    }

    protected String readString(DirectBuffer buf, int offset, int length)
    {

        final byte[] arr = new byte[length];
        buf.getBytes(offset, arr);
        return new String(arr);

    }

}
