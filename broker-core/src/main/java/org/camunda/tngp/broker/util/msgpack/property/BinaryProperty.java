package org.camunda.tngp.broker.util.msgpack.property;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.value.BinaryValue;

public class BinaryProperty extends BaseProperty<BinaryValue>
{
    public BinaryProperty(String keyString)
    {
        super(keyString, new BinaryValue());
    }

    public DirectBuffer getValue()
    {
        return getPropertyValue().getValue();
    }

    public void setValue(DirectBuffer data)
    {
        getPropertyValue().wrap(data);
        ensureSet();
    }

    public void setValue(DirectBuffer data, int offset, int length)
    {
        getPropertyValue().wrap(data, offset, length);
        ensureSet();
    }
}
