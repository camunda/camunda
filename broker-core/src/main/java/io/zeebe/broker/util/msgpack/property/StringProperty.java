package io.zeebe.broker.util.msgpack.property;

import static io.zeebe.util.StringUtil.getBytes;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.value.StringValue;


public class StringProperty extends BaseProperty<StringValue>
{

    public StringProperty(final String key)
    {
        super(key, new StringValue());
    }

    public StringProperty(final String key, final String defaultValue)
    {
        super(key, new StringValue(), new StringValue(defaultValue));
    }

    public DirectBuffer getValue()
    {
        return resolveValue().getValue();
    }

    public void setValue(final String value)
    {
        this.value.wrap(getBytes(value));
        this.isSet = true;
    }

    public void setValue(final DirectBuffer buffer)
    {
        setValue(buffer, 0, buffer.capacity());
    }

    public void setValue(final DirectBuffer buffer, final int offset, final int length)
    {
        this.value.wrap(buffer, offset, length);
        this.isSet = true;
    }
}
