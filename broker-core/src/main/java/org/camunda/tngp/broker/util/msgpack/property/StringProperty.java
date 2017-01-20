package org.camunda.tngp.broker.util.msgpack.property;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.value.StringValue;

public class StringProperty extends BaseProperty<StringValue>
{
    public StringProperty(String key)
    {
        super(key, new StringValue());
    }

    public DirectBuffer getValue()
    {
        return getPropertyValue().getValue();
    }

    public void setValue(String value)
    {
        getPropertyValue().wrap(value.getBytes(StandardCharsets.UTF_8));
        ensureSet();
    }

    public void setValue(DirectBuffer buffer, int offset, int length)
    {
        getPropertyValue().wrap(buffer, offset, length);
        ensureSet();
    }
}
