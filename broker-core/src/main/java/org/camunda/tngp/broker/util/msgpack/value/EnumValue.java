package org.camunda.tngp.broker.util.msgpack.value;

import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

public class EnumValue<E extends Enum<E>> extends BaseValue
{
    private final StringValue decodedValue = new StringValue();

    private final StringValue[] binaryEnumValues;
    private final E[] enumConstants;

    private E value;

    public EnumValue(Class<E> e, E defaultValue)
    {
        enumConstants = e.getEnumConstants();
        binaryEnumValues = new StringValue[enumConstants.length];

        for (int i = 0; i < enumConstants.length; i++)
        {
            final E constant = enumConstants[i];
            binaryEnumValues[i] = new StringValue(constant.toString());
        }

        this.value = defaultValue;
    }

    public EnumValue(Class<E> e)
    {
        this(e, null);
    }

    public E getValue()
    {
        return value;
    }

    public void setValue(E val)
    {
        this.value = val;
    }

    @Override
    public void reset()
    {
        value = null;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        binaryEnumValues[value.ordinal()].writeJSON(builder);
    }

    @Override
    public void write(MsgPackWriter writer)
    {
        binaryEnumValues[value.ordinal()].write(writer);
    }

    @Override
    public void read(MsgPackReader reader)
    {
        decodedValue.read(reader);

        for (int i = 0; i < binaryEnumValues.length; i++)
        {
            final StringValue val = binaryEnumValues[i];

            if (val.equals(decodedValue))
            {
                value = enumConstants[i];
                return;
            }
        }

        throw new RuntimeException(String.format("Illegal enum value: %s.", decodedValue.toString()));
    }

    @Override
    public int getEncodedLength()
    {
        return binaryEnumValues[value.ordinal()].getEncodedLength();
    }
}
