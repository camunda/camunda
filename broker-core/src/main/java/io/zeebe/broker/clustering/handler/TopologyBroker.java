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
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.value.ArrayValue;
import io.zeebe.msgpack.value.ValueArray;
import org.agrona.DirectBuffer;


public class TopologyBroker extends UnpackedObject
{
    protected StringProperty hostProp = new StringProperty("host");
    protected IntegerProperty portProp = new IntegerProperty("port");


    protected ArrayProperty<BrokerPartitionState> partitionStatesProp =
        new ArrayProperty<>("partitions", ArrayValue.emptyArray(), new BrokerPartitionState());

    public TopologyBroker()
    {
        this
            .declareProperty(partitionStatesProp)
            .declareProperty(hostProp)
            .declareProperty(portProp);
    }

    public DirectBuffer getHost()
    {
        return hostProp.getValue();
    }

    public TopologyBroker setHost(final DirectBuffer host, final int offset, final int length)
    {
        this.hostProp.setValue(host, offset, length);
        return this;
    }

    public int getPort()
    {
        return portProp.getValue();
    }

    public TopologyBroker setPort(final int port)
    {
        portProp.setValue(port);
        return this;
    }

    public ValueArray<BrokerPartitionState> partitionStates()
    {
        return partitionStatesProp;
    }

}
