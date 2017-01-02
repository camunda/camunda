package org.camunda.tngp.broker.util.msgpack.value;

import org.camunda.tngp.broker.util.msgpack.MsgPackReader;
import org.camunda.tngp.broker.util.msgpack.MsgPackWriter;

public class BooleanValue extends BaseValue
{
    protected boolean val = false;

    @Override
    public void reset()
    {
        val = false;
    }

    public boolean getValue()
    {
        return val;
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
