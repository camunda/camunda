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
package io.zeebe.client.impl;

import java.util.HashMap;
import java.util.Map;

import io.zeebe.client.clustering.impl.ClientTopologyManager;

public class RoundRobinDispatchStrategy implements RequestDispatchStrategy
{

    protected final ClientTopologyManager topologyManager;
    protected Map<String, Integer> topicOffsets = new HashMap<>();

    public RoundRobinDispatchStrategy(ClientTopologyManager topologyManager)
    {
        this.topologyManager = topologyManager;
    }

    @Override
    public int determinePartition(String topic)
    {
        final Integer offset = topicOffsets.getOrDefault(topic, 0);
        topicOffsets.put(topic, offset + 1);
        return topologyManager.getPartitionForTopic(topic, offset);
    }
}
