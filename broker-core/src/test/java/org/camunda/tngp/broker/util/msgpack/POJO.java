package org.camunda.tngp.broker.util.msgpack;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.property.BinaryProperty;
import org.camunda.tngp.broker.util.msgpack.property.EnumProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;
import org.camunda.tngp.broker.util.msgpack.property.PackedProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;

public class POJO extends UnpackedObject
{

    private final EnumProperty<POJOEnum> enumProp = new EnumProperty<>("enumProp", POJOEnum.class);
    private final LongProperty longProp = new LongProperty("longProp");
    private final StringProperty stringProp = new StringProperty("stringProp");
    private final PackedProperty packedProp = new PackedProperty("packedProp");
    private final BinaryProperty binaryProp = new BinaryProperty("binaryProp");

    public POJO()
    {
        objectValue.declareProperty(enumProp)
            .declareProperty(longProp)
            .declareProperty(stringProp)
            .declareProperty(packedProp)
            .declareProperty(binaryProp);
    }

    public void setEnum(POJOEnum val)
    {
        this.enumProp.setValue(val);
    }

    public POJOEnum getEnum()
    {
        return this.enumProp.getValue();
    }

    public void setLong(long val)
    {
        this.longProp.setValue(val);
    }

    public long getLong()
    {
        return longProp.getValue();
    }

    public void setString(DirectBuffer buffer)
    {
        this.stringProp.setValue(buffer, 0, buffer.capacity());
    }

    public DirectBuffer getString()
    {
        return stringProp.getValue();
    }

    public void setPacked(DirectBuffer buffer)
    {
        this.packedProp.setValue(buffer, 0, buffer.capacity());
    }

    public DirectBuffer getPacked()
    {
        return packedProp.getValue();
    }

    public void setBinary(DirectBuffer buffer)
    {
        this.binaryProp.setValue(buffer, 0, buffer.capacity());
    }

    public DirectBuffer getBinary()
    {
        return binaryProp.getValue();
    }

    public enum POJOEnum
    {
        FOO,
        BAR;
    }
}
