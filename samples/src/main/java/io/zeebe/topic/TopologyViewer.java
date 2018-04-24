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

import java.util.Properties;

import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.impl.clustering.TopologyImpl;

public class TopologyViewer
{

    public static void main(final String[] args)
    {
        final String[] brokers = new String[] {"localhost:51015", "localhost:41015", "localhost:31015"};

        for (final String broker: brokers)
        {
            final Properties clientProperties = new Properties();
            clientProperties.put(ClientProperties.BROKER_CONTACTPOINT, broker);

            try (ZeebeClient zeebeClient = ZeebeClient.create(clientProperties))
            {
                final TopologyImpl topology = zeebeClient.requestTopology().execute();

                System.out.println("Requesting topology with initial contact point " + broker);

                System.out.println("  Topology:");
                topology.getBrokers().forEach(b ->
                {
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
}
