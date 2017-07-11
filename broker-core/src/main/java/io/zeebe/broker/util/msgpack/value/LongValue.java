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

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;

public class LongValue extends BaseValue
{
    protected long value;

    public LongValue()
    {
        this(0L);
    }

    public LongValue(long initialValue)
    {
        this.value = initialValue;
    }

    public void setValue(long val)
    {
        this.value = val;
    }

    public long getValue()
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
        value = reader.readInteger();
    }

    @Override
    public int getEncodedLength()
    {
        return MsgPackWriter.getEncodedLongValueLength(value);
    }

}
