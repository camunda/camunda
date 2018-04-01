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

import io.zeebe.broker.clustering.base.topology.TopologyDto.BrokerDto;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.*;
import org.agrona.DirectBuffer;

public class PartitionEvent extends UnpackedObject
{
    protected final EnumProperty<PartitionState> state = new EnumProperty<>("state", PartitionState.class);
    protected final StringProperty topicName = new StringProperty("topicName");
    protected final IntegerProperty partitionId = new IntegerProperty("partitionId");
    protected final IntegerProperty replicationFactor = new IntegerProperty("replicationFactor");

    // TODO: this property can be removed when we have timestamps in log entries
    protected final LongProperty creationTimeout = new LongProperty("creationTimeout", -1L);

    protected final ObjectProperty<BrokerDto> creator = new ObjectProperty<>("creator", new BrokerDto());

    public PartitionEvent()
    {
        this
            .declareProperty(state)
            .declareProperty(partitionId)
            .declareProperty(replicationFactor)
            .declareProperty(topicName)
            .declareProperty(creationTimeout)
            .declareProperty(creator);
    }

    public void setState(PartitionState state)
    {
        this.state.setValue(state);
    }

    public PartitionState getState()
    {
        return state.getValue();
    }

    public void setTopicName(DirectBuffer buffer)
    {
        this.topicName.setValue(buffer);
    }

    public DirectBuffer getTopicName()
    {
        return topicName.getValue();
    }

    public void setParitionId(int id)
    {
        this.partitionId.setValue(id);
    }

    public int getPartitionId()
    {
        return partitionId.getValue();
    }

    public int getReplicationFactor()
    {
        return replicationFactor.getValue();
    }

    public void setReplicationFactor(int replicationFactor)
    {
        this.replicationFactor.setValue(replicationFactor);
    }

    public void setCreationTimeout(long timeout)
    {
        creationTimeout.setValue(timeout);
    }

    public long getCreationTimeout()
    {
        return creationTimeout.getValue();
    }

    public void setCreator(DirectBuffer host, int port)
    {
        final BrokerDto address = creator.getValue();
        address.setHost(host, 0, host.capacity());
        address.setPort(port);
    }

    public BrokerDto getCreator()
    {
        return creator.getValue();
    }
}
