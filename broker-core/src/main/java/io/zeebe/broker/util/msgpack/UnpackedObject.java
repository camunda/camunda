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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.broker.util.msgpack.value.ObjectValue;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class UnpackedObject extends ObjectValue implements Recyclable, BufferReader, BufferWriter
{
    protected final MsgPackReader reader = new MsgPackReader();
    protected final MsgPackWriter writer = new MsgPackWriter();

    public void wrap(DirectBuffer buff)
    {
        wrap(buff, 0, buff.capacity());
    }

    public void wrap(DirectBuffer buff, int offset, int length)
    {
        reader.wrap(buff, offset, length);
        try
        {
            read(reader);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not deserialize object. Deserialization stuck at offset " + reader.getOffset(), e);
        }
    }

    @Override
    public int getLength()
    {
        return getEncodedLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        writer.wrap(buffer, offset);
        write(writer);
    }

}
