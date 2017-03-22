package org.camunda.tngp.broker.util.msgpack.property;

import org.camunda.tngp.broker.util.msgpack.value.BooleanValue;

public class BooleanProperty extends BaseProperty<BooleanValue>
{

    public BooleanProperty(String key)
    {
        super(key, new BooleanValue());
    }

    public BooleanProperty(String key, boolean defaultValue)
    {
        super(key, new BooleanValue(), new BooleanValue(defaultValue));
    }

    public boolean getValue()
    {
        return resolveValue().getValue();
    }

    public void setValue(boolean value)
    {
        this.value.setValue(value);
        this.isSet = true;
    }

}
