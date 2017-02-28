package org.camunda.tngp.broker.util.msgpack;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.POJO.POJOEnum;
import org.camunda.tngp.broker.util.msgpack.property.BinaryProperty;
import org.camunda.tngp.broker.util.msgpack.property.EnumProperty;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;
import org.camunda.tngp.broker.util.msgpack.property.PackedProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;

public class AllTypesDefaultValuesPOJO extends UnpackedObject
{

    private final EnumProperty<POJOEnum> enumProp;
    private final LongProperty longProp;
    private final IntegerProperty intProp;
    private final StringProperty stringProp;
    private final PackedProperty packedProp;
    private final BinaryProperty binaryProp;

    public AllTypesDefaultValuesPOJO(
            POJOEnum enumDefault,
            long longDefault,
            int intDefault,
            String stringDefault,
            DirectBuffer packedDefault,
            DirectBuffer binaryDefault)
    {
        enumProp = new EnumProperty<>("enumProp", POJOEnum.class, enumDefault);
        longProp = new LongProperty("longProp", longDefault);
        intProp = new IntegerProperty("intProp", intDefault);
        stringProp = new StringProperty("stringProp", stringDefault);
        packedProp = new PackedProperty("packedProp", packedDefault);
        binaryProp = new BinaryProperty("binaryProp", binaryDefault);

        this.declareProperty(enumProp)
            .declareProperty(longProp)
            .declareProperty(intProp)
            .declareProperty(stringProp)
            .declareProperty(packedProp)
            .declareProperty(binaryProp);
    }

    public POJOEnum getEnum()
    {
        return enumProp.getValue();
    }

    public long getLong()
    {
        return longProp.getValue();
    }

    public int getInt()
    {
        return intProp.getValue();
    }

    public DirectBuffer getString()
    {
        return stringProp.getValue();
    }

    public DirectBuffer getPacked()
    {
        return packedProp.getValue();
    }

    public DirectBuffer getBinary()
    {
        return binaryProp.getValue();
    }

}
