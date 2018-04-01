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

import org.agrona.DirectBuffer;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.StringProperty;

public class TopicEvent extends UnpackedObject
{

    protected final EnumProperty<TopicState> state = new EnumProperty<>("state", TopicState.class);

    protected final StringProperty name = new StringProperty("name");
    protected final IntegerProperty partitions = new IntegerProperty("partitions");
    protected final IntegerProperty replicationFactor = new IntegerProperty("replicationFactor", 1);

    public TopicEvent()
    {
        this
            .declareProperty(state)
            .declareProperty(name)
            .declareProperty(partitions)
            .declareProperty(replicationFactor);
    }

    public TopicState getState()
    {
        return state.getValue();
    }

    public void setState(TopicState state)
    {
        this.state.setValue(state);
    }

    public DirectBuffer getName()
    {
        return name.getValue();
    }

    public void setName(DirectBuffer name)
    {
        this.name.setValue(name);
    }

    public int getPartitions()
    {
        return partitions.getValue();
    }

    public void setPartitions(int partitions)
    {
        this.partitions.setValue(partitions);
    }

    public void setReplicationFactor(int replicationFactor)
    {
        this.replicationFactor.setValue(replicationFactor);
    }

    public int getReplicationFactor()
    {
        return replicationFactor.getValue();
    }
}
