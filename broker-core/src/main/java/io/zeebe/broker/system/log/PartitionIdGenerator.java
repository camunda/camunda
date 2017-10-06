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
package io.zeebe.broker.system.log;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.protocol.Protocol;

public class PartitionIdGenerator extends UnpackedObject
{
    protected IntegerProperty id = new IntegerProperty("id", Protocol.SYSTEM_PARTITION + 1);

    public PartitionIdGenerator()
    {
        declareProperty(id);
    }

    public int currentId()
    {
        return currentId(0);
    }

    public int currentId(int offset)
    {
        return this.id.getValue() + offset;
    }

    public void moveToNextId()
    {
        moveToNextIds(1);
    }

    public void moveToNextIds(int offset)
    {
        final int id = this.id.getValue();
        this.id.setValue(id + offset);
    }

}
