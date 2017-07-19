/*
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
package io.zeebe.broker.util.msgpack;

import io.zeebe.msgpack.UnpackedObject;
import org.agrona.DirectBuffer;
import io.zeebe.broker.util.msgpack.POJO.POJOEnum;
import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.ObjectProperty;
import io.zeebe.msgpack.property.PackedProperty;
import io.zeebe.msgpack.property.StringProperty;

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
