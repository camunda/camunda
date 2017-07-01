package io.zeebe.broker.util.msgpack.property;

import io.zeebe.broker.util.msgpack.value.EnumValue;

public class EnumProperty<E extends Enum<E>> extends BaseProperty<EnumValue<E>>
{
    public EnumProperty(String key, Class<E> type)
    {
        super(key, new EnumValue<>(type));
    }

    public EnumProperty(String key, Class<E> type, E defaultValue)
    {
        super(key, new EnumValue<>(type), new EnumValue<>(type, defaultValue));
    }

    public E getValue()
    {
        return resolveValue().getValue();
    }

    public void setValue(E value)
    {
        this.value.setValue(value);
        this.isSet = true;
    }

}
