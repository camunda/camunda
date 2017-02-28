package org.camunda.tngp.broker.util.msgpack.property;

import org.camunda.tngp.broker.util.msgpack.value.ArrayValue;
import org.camunda.tngp.broker.util.msgpack.value.ArrayValueIterator;
import org.camunda.tngp.broker.util.msgpack.value.BaseValue;

public class ArrayProperty<T extends BaseValue> extends BaseProperty<ArrayValue<T>> implements ArrayValueIterator<T>
{
    public ArrayProperty(String keyString, ArrayValue<T> value)
    {
        super(keyString, value);
    }

    public ArrayProperty(String key, ArrayValue<T> value, ArrayValue<T> defaultValue)
    {
        super(key, value, defaultValue);
    }

    @Override
    public boolean hasNext()
    {
        return resolveValue().hasNext();
    }

    @Override
    public T next()
    {
        return resolveValue().next();
    }

    @Override
    public void remove()
    {
        if (!isSet)
        {
            isSet = true;

            if (defaultValue != null)
            {
                value.wrapReadValues(defaultValue);
            }
        }

        value.remove();
    }

    @Override
    public T add()
    {
        if (!isSet)
        {
            isSet = true;

            if (defaultValue != null)
            {
                value.wrapReadValues(defaultValue);
            }
        }

        return value.add();
    }

}
