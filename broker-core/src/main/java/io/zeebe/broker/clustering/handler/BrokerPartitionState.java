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
package io.zeebe.broker.clustering.handler;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.raft.state.RaftState;
import org.agrona.DirectBuffer;


public class BrokerPartitionState extends UnpackedObject
{
    private final EnumProperty<RaftState> stateProp = new EnumProperty<>("state", RaftState.class);
    protected StringProperty topicNameProp = new StringProperty("topicName");
    protected IntegerProperty partitionIdProp = new IntegerProperty("partitionId");

    public BrokerPartitionState()
    {
        this
            .declareProperty(stateProp)
            .declareProperty(topicNameProp)
            .declareProperty(partitionIdProp);
    }

    public RaftState getState()
    {
        return stateProp.getValue();
    }

    public BrokerPartitionState setState(RaftState eventType)
    {
        this.stateProp.setValue(eventType);
        return this;
    }

    public DirectBuffer getTopicNameProp()
    {
        return topicNameProp.getValue();
    }

    public BrokerPartitionState setTopicName(final DirectBuffer topicName, final int offset, final int length)
    {
        this.topicNameProp.setValue(topicName, offset, length);
        return this;
    }

    public int getPartitionId()
    {
        return partitionIdProp.getValue();
    }

    public BrokerPartitionState setPartitionId(final int partitionId)
    {
        partitionIdProp.setValue(partitionId);
        return this;
    }
}
