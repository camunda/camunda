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
package io.zeebe.broker.task.data;

import io.zeebe.msgpack.UnpackedObject;
import org.agrona.DirectBuffer;

import io.zeebe.msgpack.property.StringProperty;

public class TaskHeader extends UnpackedObject
{
    private final StringProperty keyProp = new StringProperty("key");
    private final StringProperty valueProp = new StringProperty("value");

    public TaskHeader()
    {
        this.declareProperty(keyProp)
            .declareProperty(valueProp);
    }

    public DirectBuffer getKey()
    {
        return keyProp.getValue();
    }

    public TaskHeader setKey(String key)
    {
        this.keyProp.setValue(key);
        return this;
    }

    public DirectBuffer getValue()
    {
        return valueProp.getValue();
    }

    public TaskHeader setValue(String value)
    {
        this.valueProp.setValue(value);
        return this;
    }

}
