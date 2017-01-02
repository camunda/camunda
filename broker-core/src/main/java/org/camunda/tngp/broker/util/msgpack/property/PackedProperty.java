package org.camunda.tngp.broker.util.msgpack.property;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.value.PackedValue;

public class PackedProperty extends BaseProperty<PackedValue>
{
    public PackedProperty(String key)
    {
        super(key, new PackedValue());
    }

    public DirectBuffer getValue()
    {
        return getPropertyValue().getValue();
    }

    public void setValue(DirectBuffer buffer, int offset, int length)
    {
        getPropertyValue().wrap(buffer, offset, length);
        ensureSet();
    }

}
