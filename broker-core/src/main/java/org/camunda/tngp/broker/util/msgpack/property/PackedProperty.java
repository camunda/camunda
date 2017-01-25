package org.camunda.tngp.broker.util.msgpack.property;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.value.PackedValue;

public class PackedProperty extends BaseProperty<PackedValue>
{
    public PackedProperty(String key)
    {
        super(key, new PackedValue());
    }

    public PackedProperty(String key, DirectBuffer defaultValue)
    {
        super(key, new PackedValue(), new PackedValue(defaultValue, 0, defaultValue.capacity()));
    }

    public DirectBuffer getValue()
    {
        return resolveValue().getValue();
    }

    public void setValue(DirectBuffer buffer, int offset, int length)
    {
        value.wrap(buffer, offset, length);
        this.isSet = true;
    }

}
