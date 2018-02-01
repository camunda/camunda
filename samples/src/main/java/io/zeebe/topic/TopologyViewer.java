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
package io.zeebe.topic;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.TopologyResponse;
import io.zeebe.client.topic.Partition;
import io.zeebe.client.topic.Topics;

public class TopologyViewer
{

    public static void main(final String[] args)
    {
        final String broker = "localhost:51015";

        final Properties clientProperties = new Properties();
        clientProperties.put(ClientProperties.BROKER_CONTACTPOINT, broker);

        try (ZeebeClient zeebeClient = ZeebeClient.create(clientProperties))
        {
            final Topics topics = zeebeClient.topics().getTopics().execute();
            final TopologyResponse topology = zeebeClient.requestTopology().execute();

            System.out.println("Requesting topics and topology with inital contact point " + broker);

            System.out.println("  Topics:");
            topics.getTopics().forEach(topic -> {
                final List<Integer> partitions = topic.getPartitions().stream().map(Partition::getId).collect(Collectors.toList());
                System.out.println("    Topic: " + topic.getName() + " Partitions: " + partitions);
            });

            System.out.println("  Topology:");
            topology.getBrokers().forEach(b -> {
                System.out.println("    " + b.getSocketAddress());
                b.getPartitions().forEach(p -> System.out.println("      " + p.getTopicName() + "." + p.getPartitionId() + " - " + p.getState()));
            });
        }
        catch (final Exception e)
        {
            System.out.println("Broker " + broker + " not available");
        }

    }
}
