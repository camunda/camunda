package org.camunda.tngp.broker.util.msgpack;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.util.msgpack.value.ObjectValue;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class UnpackedObject implements Recyclable, BufferReader, BufferWriter
{
    protected final MsgPackReader reader = new MsgPackReader();
    protected final MsgPackWriter writer = new MsgPackWriter();

    protected final ObjectValue objectValue = new ObjectValue();

    public void wrap(DirectBuffer buff)
    {
        wrap(buff, 0, buff.capacity());
    }

    public void wrap(DirectBuffer buff, int offset, int length)
    {
        reader.wrap(buff, offset, length);
        objectValue.read(reader);
    }

    @Override
    public int getLength()
    {
        return objectValue.getEncodedLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        writer.wrap(buffer, offset);
        objectValue.write(writer);
    }

    @Override
    public void reset()
    {
        objectValue.reset();
    }

    public ObjectValue getObjectValue()
    {
        return objectValue;
    }

    @Override
    public String toString()
    {
        return objectValue.toString();
    }

}
