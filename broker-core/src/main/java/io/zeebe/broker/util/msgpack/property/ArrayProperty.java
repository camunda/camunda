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

import io.zeebe.broker.util.msgpack.value.ArrayValue;
import io.zeebe.broker.util.msgpack.value.ArrayValueIterator;
import io.zeebe.broker.util.msgpack.value.BaseValue;

public class ArrayProperty<T extends BaseValue> extends BaseProperty<ArrayValue<T>> implements ArrayValueIterator<T>
{
    public ArrayProperty(String keyString, ArrayValue<T> value, T innerValue)
    {
        super(keyString, value);
        value.setInnerValue(innerValue);
    }

    public ArrayProperty(String key, ArrayValue<T> value, ArrayValue<T> defaultValue, T innerValue)
    {
        super(key, value, defaultValue);
        value.setInnerValue(innerValue);
        defaultValue.setInnerValue(innerValue);
    }

    @Override
    public boolean hasNext()
    {
        return resolveValue().hasNext();
    }

    @Override
    public T next()
    {
        return resolveValue().next();
    }

    @Override
    public void remove()
    {
        if (!isSet)
        {
            isSet = true;

            if (defaultValue != null)
            {
                value.wrapArrayValue(defaultValue);
            }
        }

        value.remove();
    }

    @Override
    public T add()
    {
        if (!isSet)
        {
            isSet = true;

            if (defaultValue != null)
            {
                value.wrapArrayValue(defaultValue);
            }
        }

        return value.add();
    }

}
