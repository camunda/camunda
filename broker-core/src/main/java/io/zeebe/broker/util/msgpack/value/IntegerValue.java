package io.zeebe.broker.util.msgpack.value;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;

public class IntegerValue extends BaseValue
{
    protected int value;

    public IntegerValue()
    {
        this(0);
    }

    public IntegerValue(int initialValue)
    {
        this.value = initialValue;
    }

    public void setValue(int val)
    {
        this.value = val;
    }

    public int getValue()
    {
        return value;
    }

    @Override
    public void reset()
    {
        value = 0;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        builder.append(value);
    }

    @Override
    public void write(MsgPackWriter writer)
    {
        writer.writeInteger(value);
    }

    @Override
    public void read(MsgPackReader reader)
    {
        final long longValue = reader.readInteger();

        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE)
        {
            throw new RuntimeException(String.format("Value doesn't fit into an integer: %s.", longValue));
        }

        value = (int) longValue;
    }

    @Override
    public int getEncodedLength()
    {
        return MsgPackWriter.getEncodedLongValueLength(value);
    }

}
