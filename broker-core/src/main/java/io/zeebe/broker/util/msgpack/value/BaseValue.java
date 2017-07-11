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

import io.zeebe.broker.util.msgpack.Recyclable;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;

public abstract class BaseValue implements Recyclable
{
    public abstract void writeJSON(StringBuilder builder);

    public abstract void write(MsgPackWriter writer);

    public abstract void read(MsgPackReader reader);

    public abstract int getEncodedLength();

    @Override
    public String toString()
    {
        final StringBuilder stringBuilder = new StringBuilder();
        writeJSON(stringBuilder);
        return stringBuilder.toString();
    }
}
