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
package io.zeebe.test.broker.protocol.brokerapi.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class Topology
{

    protected List<TopicLeader> topicLeaders = new ArrayList<>();

    public Topology()
    {
    }

    public Topology(Topology other)
    {
        this.topicLeaders = new ArrayList<>(other.topicLeaders);
    }

    public Topology addTopic(final TopicLeader topicLeader)
    {
        topicLeaders.add(topicLeader);
        return this;
    }

    public List<TopicLeader> getTopicLeaders()
    {
        return topicLeaders;
    }

    public Set<BrokerAddress> getBrokers()
    {
        return topicLeaders.stream()
            .map(topicLeader -> new BrokerAddress(topicLeader.getHost(), topicLeader.getPort()))
            .collect(Collectors.toSet());
    }

}
