package io.zeebe.broker.util.msgpack.property;

import io.zeebe.broker.util.msgpack.value.IntegerValue;

public class IntegerProperty extends BaseProperty<IntegerValue>
{
    public IntegerProperty(String key)
    {
        super(key, new IntegerValue());
    }

    public IntegerProperty(String key, int defaultValue)
    {
        super(key, new IntegerValue(), new IntegerValue(defaultValue));
    }

    public int getValue()
    {
        return resolveValue().getValue();
    }

    public void setValue(int value)
    {
        this.value.setValue(value);
        this.isSet = true;
    }

}
