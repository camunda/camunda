package org.camunda.tngp.broker.util.msgpack.value;

import org.camunda.tngp.broker.util.msgpack.Recyclable;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

public abstract class BaseValue implements Recyclable
{
    public abstract void writeJSON(StringBuilder builder);

    public abstract void write(MsgPackWriter writer);

    public abstract void read(MsgPackReader reader);

    public abstract int getEncodedLength();

    @Override
    public String toString()
    {
        final StringBuilder stringBuilder = new StringBuilder();
        writeJSON(stringBuilder);
        return stringBuilder.toString();
    }
}
