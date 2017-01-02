package org.camunda.tngp.broker.util.msgpack.property;

import org.camunda.tngp.broker.util.msgpack.value.StringValue;

public class StringProperty extends BaseProperty<StringValue>
{
    public StringProperty(String key)
    {
        super(key, new StringValue());
    }

    public StringValue getValue()
    {
        return getPropertyValue();
    }

    public void setValue(StringValue string)
    {
        getPropertyValue().wrap(string);
        ensureSet();
    }
}
