package org.camunda.tngp.broker.util.msgpack.property;

import org.camunda.tngp.broker.util.msgpack.value.ArrayValue;
import org.camunda.tngp.broker.util.msgpack.value.ArrayValueIterator;
import org.camunda.tngp.broker.util.msgpack.value.BaseValue;

public class ArrayProperty<T extends BaseValue> extends BaseProperty<ArrayValue<T>> implements ArrayValueIterator<T>
{
    public ArrayProperty(String keyString, ArrayValue<T> value, T innerValue)
    {
        super(keyString, value);
        value.setInnerValue(innerValue);
    }

    public ArrayProperty(String key, ArrayValue<T> value, ArrayValue<T> defaultValue, T innerValue)
    {
        super(key, value, defaultValue);
        value.setInnerValue(innerValue);
        defaultValue.setInnerValue(innerValue);
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
                value.wrapArrayValue(defaultValue);
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
                value.wrapArrayValue(defaultValue);
            }
        }

        return value.add();
    }

}
