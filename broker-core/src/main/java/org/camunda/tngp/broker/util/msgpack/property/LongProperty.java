package org.camunda.tngp.broker.util.msgpack.property;

import org.camunda.tngp.broker.util.msgpack.value.LongValue;

public class LongProperty extends BaseProperty<LongValue>
{
    public LongProperty(String key)
    {
        super(key, new LongValue());
    }

    public long getValue()
    {
        return getPropertyValue().getValue();
    }

    public void setValue(long value)
    {
        getPropertyValue().setValue(value);
        ensureSet();
    }

}
