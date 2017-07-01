package io.zeebe.broker.util.msgpack;

import io.zeebe.broker.util.msgpack.property.LongProperty;

public class MinimalPOJO extends UnpackedObject
{

    private final LongProperty longProp = new LongProperty("longProp");

    public MinimalPOJO()
    {
        this.declareProperty(longProp);
    }

    public long getLongProp()
    {
        return longProp.getValue();
    }

    public void setLongProp(final long value)
    {
        this.longProp.setValue(value);
    }

}
