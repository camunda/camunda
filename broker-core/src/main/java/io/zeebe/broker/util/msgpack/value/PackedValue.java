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
package io.zeebe.broker.util.msgpack.value;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;

public class PackedValue extends BaseValue
{
    private final DirectBuffer buffer = new UnsafeBuffer(0, 0);
    private int length;

    public PackedValue()
    {

    }

    public PackedValue(DirectBuffer defaultValue, int offset, int length)
    {
        wrap(defaultValue, offset, length);
    }

    public void wrap(DirectBuffer buff, int offset, int length)
    {
        this.buffer.wrap(buff, offset, length);
        this.length = length;
    }

    public DirectBuffer getValue()
    {
        return buffer;
    }

    @Override
    public void reset()
    {
        buffer.wrap(0, 0);
        length = 0;
    }

    @Override
    public void read(MsgPackReader reader)
    {
        final DirectBuffer buffer = reader.getBuffer();
        final int offset = reader.getOffset();
        reader.skipValue();
        final int lenght = reader.getOffset() - offset;

        wrap(buffer, offset, lenght);
    }

    @Override
    public void write(MsgPackWriter writer)
    {
        writer.writeRaw(buffer);
    }

    @Override
    public int getEncodedLength()
    {
        return length;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        builder.append("[packed value (length=");
        builder.append(length);
        builder.append(")]");
    }
}
