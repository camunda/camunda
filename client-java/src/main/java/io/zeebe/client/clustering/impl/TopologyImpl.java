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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.agrona.collections.Int2ObjectHashMap;

import io.zeebe.client.clustering.Topology;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.CollectionUtil;


public class TopologyImpl implements Topology
{
    protected Int2ObjectHashMap<RemoteAddress> topicLeaders;
    protected List<RemoteAddress> brokers;
    protected final Random randomBroker = new Random();
    protected Map<String, List<Integer>> partitionsByTopic = new HashMap<>();

    public TopologyImpl()
    {
        topicLeaders = new Int2ObjectHashMap<>();
        brokers = new ArrayList<>();
    }

    public void addBroker(RemoteAddress remoteAddress)
    {
        brokers.add(remoteAddress);
    }

    @Override
    public RemoteAddress getLeaderForPartition(int partition)
    {
        return topicLeaders.get(partition);
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
    public List<Integer> getPartitionsOfTopic(String topic)
    {
        return partitionsByTopic.get(topic);
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
            topicLeaders.put(leader.getPartitionId(), transport.registerRemoteAddress(leader.getSocketAddress()));
            CollectionUtil.addToMapOfLists(partitionsByTopic, leader.getTopicName(), leader.getPartitionId());
        }

    }

}
