package org.camunda.tngp.broker.util.msgpack.property;

import org.camunda.tngp.broker.util.msgpack.value.EnumValue;

public class EnumProperty<E extends Enum<E>> extends BaseProperty<EnumValue<E>>
{
    public EnumProperty(String key, Class<E> type)
    {
        super(key, new EnumValue<>(type));
    }

    public E getValue()
    {
        return getPropertyValue().getValue();
    }

    public void setValue(E value)
    {
        getPropertyValue().setValue(value);
        ensureSet();
    }

}
