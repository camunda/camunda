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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;

public class BinaryValue extends BaseValue
{
    protected final MutableDirectBuffer data = new UnsafeBuffer(0, 0);
    protected int length = 0;

    public BinaryValue()
    {
    }

    public BinaryValue(DirectBuffer initialValue, int offset, int length)
    {
        wrap(initialValue, offset, length);
    }

    @Override
    public void reset()
    {
        data.wrap(0, 0);
        length = 0;
    }

    public void wrap(DirectBuffer buff)
    {
        wrap(buff, 0, buff.capacity());
    }

    public void wrap(DirectBuffer buff, int offset, int length)
    {
        if (length == 0)
        {
            this.data.wrap(0, 0);
        }
        else
        {
            this.data.wrap(buff, offset, length);
        }
        this.length = length;
    }

    public void wrap(StringValue decodedKey)
    {
        this.wrap(decodedKey.getValue());
    }

    public DirectBuffer getValue()
    {
        return data;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        final byte[] bytes = new byte[length];
        data.getBytes(0, bytes);

        builder.append("\"");
        builder.append(new String(Base64.getEncoder().encode(bytes), StandardCharsets.UTF_8));
        builder.append("\"");
    }

    @Override
    public void write(MsgPackWriter writer)
    {
        writer.writeBinary(data);
    }

    @Override
    public void read(MsgPackReader reader)
    {
        final DirectBuffer buffer = reader.getBuffer();
        final int stringLength = reader.readBinaryLength();
        final int offset = reader.getOffset();

        reader.skipBytes(stringLength);

        this.wrap(buffer, offset, stringLength);
    }

    @Override
    public int getEncodedLength()
    {
        return MsgPackWriter.getEncodedBinaryValueLength(length);
    }

}
