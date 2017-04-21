package org.camunda.tngp.broker.util.msgpack.property;

import static org.camunda.tngp.util.StringUtil.getBytes;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.value.StringValue;


public class StringProperty extends BaseProperty<StringValue>
{

    public StringProperty(String key)
    {
        super(key, new StringValue());
    }

    public StringProperty(String key, String defaultValue)
    {
        super(key, new StringValue(), new StringValue(defaultValue));
    }

    public DirectBuffer getValue()
    {
        return resolveValue().getValue();
    }

    public void setValue(String value)
    {
        this.value.wrap(getBytes(value));
        this.isSet = true;
    }

    public void setValue(DirectBuffer buffer, int offset, int length)
    {
        this.value.wrap(buffer, offset, length);
        this.isSet = true;
    }
}
