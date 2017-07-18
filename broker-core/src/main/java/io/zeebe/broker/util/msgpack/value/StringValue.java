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

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;


public class StringValue extends BaseValue
{
    public static final String EMPTY_STRING = "";

    private MutableDirectBuffer bytes = new UnsafeBuffer(0, 0);
    private int length;
    private int hashCode;

    public StringValue()
    {
        this(EMPTY_STRING);
    }

    public StringValue(String string)
    {
        this(wrapString(string));
    }

    public StringValue(DirectBuffer buffer)
    {
        this(buffer, 0, buffer.capacity());
    }

    public StringValue(DirectBuffer buffer, int offset, int length)
    {
        wrap(buffer, offset, length);
    }

    @Override
    public void reset()
    {
        bytes.wrap(0, 0);
        length = 0;
        hashCode = 0;
    }

    public void wrap(byte[] bytes)
    {
        this.bytes.wrap(bytes);
        this.length = bytes.length;
        this.hashCode = 0;
    }

    public void wrap(DirectBuffer buff)
    {
        wrap(buff, 0, buff.capacity());
    }

    public void wrap(DirectBuffer buff, int offset, int length)
    {
        if (length == 0)
        {
            this.bytes.wrap(0, 0);
        }
        else
        {
            this.bytes.wrap(buff, offset, length);
        }
        this.length = length;
        this.hashCode = 0;
    }

    public void wrap(StringValue anotherString)
    {
        this.wrap(anotherString.getValue());
    }

    public int getLength()
    {
        return length;
    }

    public DirectBuffer getValue()
    {
        return bytes;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        builder.append("\"");
        builder.append(toString());
        builder.append("\"");
    }

    @Override
    public String toString()
    {
        return bytes.getStringWithoutLengthUtf8(0, length);
    }

    @Override
    public boolean equals(Object s)
    {
        if (s == null)
        {
            return false;
        }
        else if (s instanceof StringValue)
        {
            final StringValue otherString = (StringValue) s;
            final MutableDirectBuffer otherBytes = otherString.bytes;
            return bytes.equals(otherBytes);
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        if (hashCode == 0 && length > 0)
        {
            hashCode = bytes.hashCode();
        }

        return hashCode;
    }

    @Override
    public void read(MsgPackReader reader)
    {
        final DirectBuffer buffer = reader.getBuffer();
        final int stringLength = reader.readStringLength();
        final int offset = reader.getOffset();

        reader.skipBytes(stringLength);

        this.wrap(buffer, offset, stringLength);
    }

    @Override
    public void write(MsgPackWriter writer)
    {
        writer.writeString(bytes);
    }

    @Override
    public int getEncodedLength()
    {
        return MsgPackWriter.getEncodedStringLength(length);
    }
}
