package org.camunda.tngp.client.benchmark.msgpack;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.BinaryProperty;
import org.camunda.tngp.broker.util.msgpack.property.EnumProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;
import org.camunda.tngp.broker.util.msgpack.property.PackedProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

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
            writer.writeString(new UnsafeBuffer(k.getBytes(StandardCharsets.UTF_8)));
            writer.writeString(new UnsafeBuffer(v.getBytes(StandardCharsets.UTF_8)));
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
