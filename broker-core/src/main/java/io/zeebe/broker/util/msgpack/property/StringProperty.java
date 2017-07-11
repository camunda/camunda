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
package io.zeebe.broker.util.msgpack.property;

import static io.zeebe.util.StringUtil.getBytes;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.value.StringValue;


public class StringProperty extends BaseProperty<StringValue>
{

    public StringProperty(final String key)
    {
        super(key, new StringValue());
    }

    public StringProperty(final String key, final String defaultValue)
    {
        super(key, new StringValue(), new StringValue(defaultValue));
    }

    public DirectBuffer getValue()
    {
        return resolveValue().getValue();
    }

    public void setValue(final String value)
    {
        this.value.wrap(getBytes(value));
        this.isSet = true;
    }

    public void setValue(final DirectBuffer buffer)
    {
        setValue(buffer, 0, buffer.capacity());
    }

    public void setValue(final DirectBuffer buffer, final int offset, final int length)
    {
        this.value.wrap(buffer, offset, length);
        this.isSet = true;
    }
}
