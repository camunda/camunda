package org.camunda.tngp.broker.util.msgpack;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.util.msgpack.value.ObjectValue;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class UnpackedObject extends ObjectValue implements Recyclable, BufferReader, BufferWriter
{
    protected final MsgPackReader reader = new MsgPackReader();
    protected final MsgPackWriter writer = new MsgPackWriter();

    public void wrap(DirectBuffer buff)
    {
        wrap(buff, 0, buff.capacity());
    }

    public void wrap(DirectBuffer buff, int offset, int length)
    {
        reader.wrap(buff, offset, length);
        try
        {
            read(reader);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not deserialize object. Deserialization stuck at offset " + reader.getOffset(), e);
        }
    }

    @Override
    public int getLength()
    {
        return getEncodedLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        writer.wrap(buffer, offset);
        write(writer);
    }

}
