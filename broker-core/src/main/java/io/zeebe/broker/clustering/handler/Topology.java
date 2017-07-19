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
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.value.ArrayValue;
import io.zeebe.msgpack.value.ArrayValueIterator;
import io.zeebe.msgpack.spec.MsgPackHelper;


public class Topology extends UnpackedObject
{
    protected static final DirectBuffer EMPTY_ARRAY = new UnsafeBuffer(MsgPackHelper.EMPTY_ARRAY);

    protected ArrayProperty<TopicLeader> topicLeadersProp = new ArrayProperty<>("topicLeaders",
        new ArrayValue<>(),
        new ArrayValue<>(EMPTY_ARRAY, 0, EMPTY_ARRAY.capacity()),
        new TopicLeader());

    protected ArrayProperty<BrokerAddress> brokersProp = new ArrayProperty<>("brokers",
        new ArrayValue<>(),
        new ArrayValue<>(EMPTY_ARRAY, 0, EMPTY_ARRAY.capacity()),
        new BrokerAddress());

    public Topology()
    {
        this
            .declareProperty(topicLeadersProp)
            .declareProperty(brokersProp);
    }

    public ArrayValueIterator<TopicLeader> topicLeaders()
    {
        return topicLeadersProp;
    }

    public ArrayProperty<BrokerAddress> brokers()
    {
        return brokersProp;
    }

}
