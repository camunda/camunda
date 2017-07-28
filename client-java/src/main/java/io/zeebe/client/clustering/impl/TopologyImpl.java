/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.clustering.impl;

import java.util.*;

import io.zeebe.client.clustering.Topology;
import io.zeebe.client.impl.Partition;
import io.zeebe.transport.*;


public class TopologyImpl implements Topology
{
    protected Map<Partition, RemoteAddress> topicLeaders;
    protected List<RemoteAddress> brokers;
    protected final Random randomBroker = new Random();

    public TopologyImpl()
    {
        topicLeaders = new HashMap<>();
        brokers = new ArrayList<>();
    }

    public void addBroker(RemoteAddress remoteAddress)
    {
        brokers.add(remoteAddress);
    }

    @Override
    public RemoteAddress getLeaderForTopic(Partition topic)
    {
        if (topic != null)
        {
            return topicLeaders.get(topic);
        }
        else
        {
            return getRandomBroker();
        }
    }

    @Override
    public RemoteAddress getRandomBroker()
    {
        if (!brokers.isEmpty())
        {
            final int nextBroker = randomBroker.nextInt(brokers.size());
            return brokers.get(nextBroker);
        }
        else
        {
            throw new RuntimeException("Unable to select random broker from empty list");
        }
    }

    @Override
    public String toString()
    {
        return "Topology{" +
            "topicLeaders=" + topicLeaders +
            ", brokers=" + brokers +
            '}';
    }

    public void update(TopologyResponse topologyDto, ClientTransport transport)
    {
        for (SocketAddress addr : topologyDto.getBrokers())
        {
            addBroker(transport.registerRemoteAddress(addr));
        }

        for (TopicLeader leader : topologyDto.getTopicLeaders())
        {
            topicLeaders.put(leader.getTopic(), transport.registerRemoteAddress(leader.getSocketAddress()));
        }
    }

}
