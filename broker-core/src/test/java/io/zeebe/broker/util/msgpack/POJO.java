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
package io.zeebe.broker.util.msgpack;

import org.agrona.DirectBuffer;
import io.zeebe.broker.util.msgpack.property.BinaryProperty;
import io.zeebe.broker.util.msgpack.property.EnumProperty;
import io.zeebe.broker.util.msgpack.property.IntegerProperty;
import io.zeebe.broker.util.msgpack.property.LongProperty;
import io.zeebe.broker.util.msgpack.property.ObjectProperty;
import io.zeebe.broker.util.msgpack.property.PackedProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;

public class POJO extends UnpackedObject
{

    private final EnumProperty<POJOEnum> enumProp = new EnumProperty<>("enumProp", POJOEnum.class);
    private final LongProperty longProp = new LongProperty("longProp");
    private final IntegerProperty intProp = new IntegerProperty("intProp");
    private final StringProperty stringProp = new StringProperty("stringProp");
    private final PackedProperty packedProp = new PackedProperty("packedProp");
    private final BinaryProperty binaryProp = new BinaryProperty("binaryProp");
    private final ObjectProperty<POJONested> objectProp = new ObjectProperty<>("objectProp", new POJONested());

    public POJO()
    {
        this.declareProperty(enumProp)
            .declareProperty(longProp)
            .declareProperty(intProp)
            .declareProperty(stringProp)
            .declareProperty(packedProp)
            .declareProperty(binaryProp)
            .declareProperty(objectProp);
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

    public void setInt(int val)
    {
        this.intProp.setValue(val);
    }

    public int getInt()
    {
        return intProp.getValue();
    }

    public void setString(DirectBuffer buffer)
    {
        this.stringProp.setValue(buffer);
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

    public POJONested nestedObject()
    {
        return objectProp.getValue();
    }

    public enum POJOEnum
    {
        FOO,
        BAR;
    }
}
