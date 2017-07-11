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

import io.zeebe.broker.util.msgpack.value.EnumValue;

public class EnumProperty<E extends Enum<E>> extends BaseProperty<EnumValue<E>>
{
    public EnumProperty(String key, Class<E> type)
    {
        super(key, new EnumValue<>(type));
    }

    public EnumProperty(String key, Class<E> type, E defaultValue)
    {
        super(key, new EnumValue<>(type), new EnumValue<>(type, defaultValue));
    }

    public E getValue()
    {
        return resolveValue().getValue();
    }

    public void setValue(E value)
    {
        this.value.setValue(value);
        this.isSet = true;
    }

}
