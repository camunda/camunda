package org.camunda.tngp.broker.util.msgpack.property;

import org.camunda.tngp.broker.util.msgpack.value.LongValue;

public class LongProperty extends BaseProperty<LongValue>
{
    public LongProperty(String key)
    {
        super(key, new LongValue());
    }

    public LongProperty(String key, long defaultValue)
    {
        super(key, new LongValue(), new LongValue(defaultValue));
    }

    public long getValue()
    {
        return resolveValue().getValue();
    }

    public void setValue(long value)
    {
        this.value.setValue(value);
        this.isSet = true;
    }

}
