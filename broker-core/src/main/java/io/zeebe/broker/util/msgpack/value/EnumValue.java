/**
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.util.msgpack.value;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;

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
