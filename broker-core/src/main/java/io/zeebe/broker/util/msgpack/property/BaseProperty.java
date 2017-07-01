package io.zeebe.broker.util.msgpack.property;

import java.util.Objects;

import io.zeebe.broker.util.msgpack.Recyclable;
import io.zeebe.broker.util.msgpack.value.BaseValue;
import io.zeebe.broker.util.msgpack.value.StringValue;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;

public abstract class BaseProperty<T extends BaseValue> implements Recyclable
{
    protected StringValue key;
    protected T value;
    protected T defaultValue;
    protected boolean isSet;

    public BaseProperty(T value)
    {
        this(StringValue.EMPTY_STRING, value);
    }


    public BaseProperty(String keyString, T value)
    {
        this(keyString, value, null);
    }

    public BaseProperty(String keyString, T value, T defaultValue)
    {
        Objects.requireNonNull(keyString);
        Objects.requireNonNull(value);

        this.key = new StringValue(keyString);
        this.value = value;
        this.defaultValue = defaultValue;
    }

    public void set()
    {
        this.isSet = true;
    }

    @Override
    public void reset()
    {
        this.isSet = false;
        this.value.reset();
    }

    public boolean isWriteable()
    {
        return isSet || defaultValue != null;
    }

    public StringValue getKey()
    {
        return key;
    }

    protected T resolveValue()
    {
        if (isSet)
        {
            return value;
        }
        else if (defaultValue != null)
        {
            return defaultValue;
        }
        else
        {
            throw new RuntimeException(String.format("Property '%s' has no valid value", key));
        }
    }

    public int getEncodedLength()
    {
        return key.getEncodedLength() + resolveValue().getEncodedLength();
    }

    public void read(MsgPackReader reader)
    {
        value.read(reader);
        set();
    }

    public void write(MsgPackWriter writer)
    {
        T valueToWrite = value;
        if (!isSet)
        {
            valueToWrite = defaultValue;
        }

        if (valueToWrite == null)
        {
            throw new RuntimeException("Cannot write property; neither value, nor default value specified");
        }

        key.write(writer);
        valueToWrite.write(writer);
    }

    public void writeJSON(StringBuilder sb)
    {
        key.writeJSON(sb);
        sb.append(":");
        if (isWriteable())
        {
            resolveValue().writeJSON(sb);
        }
        else
        {
            sb.append("\"NO VALID WRITEABLE VALUE\"");
        }
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append(key.toString());
        builder.append(" => ");
        builder.append(value.toString());
        return builder.toString();
    }
}
