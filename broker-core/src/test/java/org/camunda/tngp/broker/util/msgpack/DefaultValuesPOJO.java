package org.camunda.tngp.broker.util.msgpack;

import org.camunda.tngp.broker.util.msgpack.property.LongProperty;

public class DefaultValuesPOJO extends UnpackedObject
{

    protected LongProperty defaultValueProperty;
    protected LongProperty noDefaultValueProperty = new LongProperty("noDefaultValueProp");

    public DefaultValuesPOJO(long defaultValue)
    {
        defaultValueProperty = new LongProperty("defaultValueProp", defaultValue);

        objectValue
            .declareProperty(defaultValueProperty)
            .declareProperty(noDefaultValueProperty);
    }

    public long getDefaultValueProperty()
    {
        return defaultValueProperty.getValue();
    }

    public void setDefaultValueProperty(long value)
    {
        this.defaultValueProperty.setValue(value);
    }

    public long getNoDefaultValueProperty()
    {
        return noDefaultValueProperty.getValue();
    }

    public void setNoDefaultValueProperty(long value)
    {
        this.noDefaultValueProperty.setValue(value);
    }
}
