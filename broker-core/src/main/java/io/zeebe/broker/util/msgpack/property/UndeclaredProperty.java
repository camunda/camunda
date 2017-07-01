package io.zeebe.broker.util.msgpack.property;

import io.zeebe.broker.util.msgpack.value.StringValue;

public class UndeclaredProperty extends PackedProperty
{
    public UndeclaredProperty()
    {
        super(StringValue.EMPTY_STRING);
    }
}
