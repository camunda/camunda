package org.camunda.tngp.broker.util.msgpack.value;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

public class PackedValue extends BaseValue
{
    private final DirectBuffer buffer = new UnsafeBuffer(0, 0);
    private int length;

    public void wrap(DirectBuffer buff, int offset, int length)
    {
        this.buffer.wrap(buff, offset, length);
        this.length = length;
    }

    public DirectBuffer getValue()
    {
        return buffer;
    }

    @Override
    public void reset()
    {
        buffer.wrap(0, 0);
        length = 0;
    }

    @Override
    public void read(MsgPackReader reader)
    {
        final DirectBuffer buffer = reader.getBuffer();
        final int offset = reader.getOffset();
        reader.skipValue();
        final int lenght = reader.getOffset() - offset;

        wrap(buffer, offset, lenght);
    }

    @Override
    public void write(MsgPackWriter writer)
    {
        writer.writeRaw(buffer);
    }

    @Override
    public int getEncodedLength()
    {
        return length;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        builder.append("[packed value (length=");
        builder.append(length);
        builder.append(")]");
    }
}
