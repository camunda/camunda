package org.camunda.tngp.broker.util.msgpack.property;

import org.camunda.tngp.broker.util.msgpack.value.StringValue;

public class UndeclaredProperty extends PackedProperty
{
    public UndeclaredProperty()
    {
        super(StringValue.EMPTY_STRING);
    }
}
