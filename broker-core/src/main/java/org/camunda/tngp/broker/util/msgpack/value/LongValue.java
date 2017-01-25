package org.camunda.tngp.broker.util.msgpack.value;

import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

public class LongValue extends BaseValue
{
    protected long value;

    public LongValue()
    {
        this(0L);
    }

    public LongValue(long initialValue)
    {
        this.value = initialValue;
    }

    public void setValue(long val)
    {
        this.value = val;
    }

    public long getValue()
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
        value = reader.readInteger();
    }

    @Override
    public int getEncodedLength()
    {
        return MsgPackWriter.getEncodedLongValueLength(value);
    }

}
