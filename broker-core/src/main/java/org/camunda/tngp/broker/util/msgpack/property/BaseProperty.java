package org.camunda.tngp.broker.util.msgpack.property;

import java.util.Objects;

import org.camunda.tngp.broker.util.msgpack.Recyclable;
import org.camunda.tngp.broker.util.msgpack.value.BaseValue;
import org.camunda.tngp.broker.util.msgpack.value.StringValue;
import org.camunda.tngp.broker.util.msgpack.value.ObjectValue;

public abstract class BaseProperty<T extends BaseValue> implements Recyclable
{
    private StringValue key;
    private T value;

    private boolean isSet;
    protected ObjectValue objectValue;

    public BaseProperty(T value)
    {
        this(StringValue.EMPTY_STRING, value);
    }

    public BaseProperty(String keyString, T value)
    {
        Objects.requireNonNull(keyString);
        Objects.requireNonNull(value);

        this.key = new StringValue(keyString);
        this.value = value;
    }

    public void init(ObjectValue objectValue)
    {
        this.objectValue = objectValue;
    }

    public void set()
    {
        this.objectValue.addProperty(this);
        this.isSet = true;
    }

    public void ensureSet()
    {
        if (!isSet)
        {
            set();
        }
    }

    public void unset()
    {
        this.objectValue.removePoperty(this);
        this.isSet = false;
    }

    @Override
    public void reset()
    {
        this.isSet = false;
        this.value.reset();
    }

    public boolean isSet()
    {
        return isSet;
    }

    public StringValue getKey()
    {
        return key;
    }

    public T getPropertyValue()
    {
        return value;
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
