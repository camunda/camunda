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
package io.zeebe.broker.clustering.handler;

import org.agrona.DirectBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.IntegerProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;


public class TopicLeader extends UnpackedObject
{
    protected StringProperty hostProp = new StringProperty("host");
    protected IntegerProperty portProp = new IntegerProperty("port");
    protected StringProperty topicNameProp = new StringProperty("topicName");
    protected IntegerProperty partitionIdProp = new IntegerProperty("partitionId");

    public TopicLeader()
    {
        this
            .declareProperty(hostProp)
            .declareProperty(portProp)
            .declareProperty(topicNameProp)
            .declareProperty(partitionIdProp);
    }

    public DirectBuffer getHost()
    {
        return hostProp.getValue();
    }

    public TopicLeader setHost(final DirectBuffer host, final int offset, final int length)
    {
        this.hostProp.setValue(host, offset, length);
        return this;
    }

    public int getPort()
    {
        return portProp.getValue();
    }

    public TopicLeader setPort(final int port)
    {
        portProp.setValue(port);
        return this;
    }

    public DirectBuffer getTopicNameProp()
    {
        return topicNameProp.getValue();
    }

    public TopicLeader setTopicName(final DirectBuffer topicName, final int offset, final int length)
    {
        this.topicNameProp.setValue(topicName, offset, length);
        return this;
    }

    public int getPartitionId()
    {
        return partitionIdProp.getValue();
    }

    public TopicLeader setPartitionId(final int partitionId)
    {
        partitionIdProp.setValue(partitionId);
        return this;
    }
}
