package io.zeebe.broker.util.msgpack;

import org.agrona.DirectBuffer;
import io.zeebe.broker.util.msgpack.POJO.POJOEnum;
import io.zeebe.broker.util.msgpack.property.BinaryProperty;
import io.zeebe.broker.util.msgpack.property.EnumProperty;
import io.zeebe.broker.util.msgpack.property.IntegerProperty;
import io.zeebe.broker.util.msgpack.property.LongProperty;
import io.zeebe.broker.util.msgpack.property.ObjectProperty;
import io.zeebe.broker.util.msgpack.property.PackedProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;

public class AllTypesDefaultValuesPOJO extends UnpackedObject
{

    private final EnumProperty<POJOEnum> enumProp;
    private final LongProperty longProp;
    private final IntegerProperty intProp;
    private final StringProperty stringProp;
    private final PackedProperty packedProp;
    private final BinaryProperty binaryProp;
    private final ObjectProperty<POJONested> objectProp;

    public AllTypesDefaultValuesPOJO(
            POJOEnum enumDefault,
            long longDefault,
            int intDefault,
            String stringDefault,
            DirectBuffer packedDefault,
            DirectBuffer binaryDefault,
            POJONested objectDefault)
    {
        enumProp = new EnumProperty<>("enumProp", POJOEnum.class, enumDefault);
        longProp = new LongProperty("longProp", longDefault);
        intProp = new IntegerProperty("intProp", intDefault);
        stringProp = new StringProperty("stringProp", stringDefault);
        packedProp = new PackedProperty("packedProp", packedDefault);
        binaryProp = new BinaryProperty("binaryProp", binaryDefault);
        objectProp = new ObjectProperty<>("objectProp", objectDefault);

        this.declareProperty(enumProp)
            .declareProperty(longProp)
            .declareProperty(intProp)
            .declareProperty(stringProp)
            .declareProperty(packedProp)
            .declareProperty(binaryProp)
            .declareProperty(objectProp);
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

    public POJONested getNestedObject()
    {
        return objectProp.getValue();
    }

}
