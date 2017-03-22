package org.camunda.tngp.broker.util.msgpack.value;

import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

public class BooleanValue extends BaseValue
{
    protected boolean val = false;

    public BooleanValue()
    {
        this(false);
    }

    public BooleanValue(boolean initialValue)
    {
        this.val = initialValue;
    }

    @Override
    public void reset()
    {
        val = false;
    }

    public boolean getValue()
    {
        return val;
    }

    public void setValue(boolean value)
    {
        this.val = value;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        builder.append(val);
    }

    @Override
    public void write(MsgPackWriter writer)
    {
        writer.writeBoolean(val);
    }

    @Override
    public void read(MsgPackReader reader)
    {
        val = reader.readBoolean();
    }

    @Override
    public int getEncodedLength()
    {
        return MsgPackWriter.getEncodedBooleanValueLength();
    }

}
