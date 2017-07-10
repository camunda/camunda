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

public class IntegerValue extends BaseValue
{
    protected int value;

    public IntegerValue()
    {
        this(0);
    }

    public IntegerValue(int initialValue)
    {
        this.value = initialValue;
    }

    public void setValue(int val)
    {
        this.value = val;
    }

    public int getValue()
    {
        return value;
    }

    @Override
    public void reset()
    {
        value = 0;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        builder.append(value);
    }

    @Override
    public void write(MsgPackWriter writer)
    {
        writer.writeInteger(value);
    }

    @Override
    public void read(MsgPackReader reader)
    {
        final long longValue = reader.readInteger();

        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE)
        {
            throw new RuntimeException(String.format("Value doesn't fit into an integer: %s.", longValue));
        }

        value = (int) longValue;
    }

    @Override
    public int getEncodedLength()
    {
        return MsgPackWriter.getEncodedLongValueLength(value);
    }

}
