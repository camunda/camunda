package io.zeebe.broker.util.msgpack.property;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.value.BinaryValue;

public class BinaryProperty extends BaseProperty<BinaryValue>
{
    public BinaryProperty(String keyString)
    {
        super(keyString, new BinaryValue());
    }

    public BinaryProperty(String keyString, DirectBuffer defaultValue)
    {
        super(keyString, new BinaryValue(), new BinaryValue(defaultValue, 0, defaultValue.capacity()));
    }

    public DirectBuffer getValue()
    {
        return resolveValue().getValue();
    }

    public void setValue(DirectBuffer data)
    {
        setValue(data, 0, data.capacity());
    }

    public void setValue(DirectBuffer data, int offset, int length)
    {
        this.value.wrap(data, offset, length);
        this.isSet = true;
    }
}
