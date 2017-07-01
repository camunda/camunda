package io.zeebe.broker.util.msgpack.value;

import io.zeebe.broker.util.msgpack.Recyclable;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;

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
