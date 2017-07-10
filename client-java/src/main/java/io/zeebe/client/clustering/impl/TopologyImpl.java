/**
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import io.zeebe.client.clustering.Topology;
import io.zeebe.client.impl.Topic;
import io.zeebe.transport.SocketAddress;


public class TopologyImpl implements Topology
{

    protected Map<Topic, SocketAddress> topicLeaders;
    protected List<SocketAddress> brokers;
    protected final Random randomBroker = new Random();

    // required for object mapper deserialization
    public TopologyImpl()
    {
        topicLeaders = new HashMap<>();
        brokers = new ArrayList<>();
    }

    public TopologyImpl(final SocketAddress... initialBrokers)
    {
        topicLeaders = new HashMap<>();
        brokers = Arrays.asList(initialBrokers);
    }

    public Map<Topic, SocketAddress> getTopicLeaders()
    {
        return topicLeaders;
    }

    // required for object mapper deserialization
    public TopologyImpl setTopicLeaders(final List<TopicLeader> topicLeaders)
    {
        this.topicLeaders = topicLeaders.stream()
            .collect(Collectors.toMap(
                TopicLeader::getTopic,
                TopicLeader::getSocketAddress
            ));

        return this;
    }

    public List<SocketAddress> getBrokers()
    {
        return brokers;
    }

    // required for object mapper deserialization
    public TopologyImpl setBrokers(final List<SocketAddress> brokers)
    {
        this.brokers = brokers;
        return this;
    }

    @Override
    public SocketAddress getLeaderForTopic(final Topic topic)
    {
        return topicLeaders.get(topic);
    }

    @Override
    public SocketAddress getRandomBroker()
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

}
