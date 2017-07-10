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

public class BooleanValue extends BaseValue
{
    protected boolean val = false;

    public BooleanValue()
    {
        this(false);
    }

    public BooleanValue(boolean initialValue)
    {
        this.val = initialValue;
    }

    @Override
    public void reset()
    {
        val = false;
    }

    public boolean getValue()
    {
        return val;
    }

    public void setValue(boolean value)
    {
        this.val = value;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        builder.append(val);
    }

    @Override
    public void write(MsgPackWriter writer)
    {
        writer.writeBoolean(val);
    }

    @Override
    public void read(MsgPackReader reader)
    {
        val = reader.readBoolean();
    }

    @Override
    public int getEncodedLength()
    {
        return MsgPackWriter.getEncodedBooleanValueLength();
    }

}
