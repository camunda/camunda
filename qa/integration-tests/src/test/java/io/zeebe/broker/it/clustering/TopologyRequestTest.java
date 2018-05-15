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
package io.zeebe.broker.it.clustering;

import static io.zeebe.broker.it.clustering.ClusteringRule.BROKER_1_CLIENT_ADDRESS;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;

import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.*;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class TopologyRequestTest
{

    public AutoCloseableRule closeables = new AutoCloseableRule();
    public Timeout testTimeout = Timeout.seconds(30);
    public ClientRule clientRule = new ClientRule(false);
    public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

    @Rule
    public RuleChain ruleChain =
        RuleChain.outerRule(closeables)
                 .around(testTimeout)
                 .around(clientRule)
                 .around(clusteringRule);

    private ZeebeClient zeebeClient;

    @Before
    public void before()
    {
        zeebeClient = clientRule.getClient();
    }

    @Test
    public void shouldUpdateClientTopologyOnTopologyRequest()
    {
        // given
        final SocketAddress oldLeader = BROKER_1_CLIENT_ADDRESS;

        // when
        clusteringRule.stopBroker(oldLeader);

        // then
        clusteringRule.createTopic("foo", 1);
    }

    @Test
    public void shouldUpdateClientTopologyOnAsyncTopologyRequest()
    {
        // given
        final SocketAddress oldLeader = BROKER_1_CLIENT_ADDRESS;

        // when
        final List<Integer> partitions = clusteringRule.getBrokersLeadingPartitions(oldLeader);
        clusteringRule.brokers.remove(oldLeader).close();

        doRepeatedly(this::requestTopologyAsync)
            .until(topologyBrokers ->
                topologyBrokers != null &&
                    topologyBrokers.stream()
                                   .filter(broker -> !broker.getSocketAddress().equals(oldLeader))
                                   .flatMap(broker -> broker.getPartitions().stream())
                                   .filter(PartitionInfo::isLeader)
                                   .map(PartitionInfo::getPartitionId)
                                   .collect(Collectors.toSet())
                                   .containsAll(partitions)
            );

        // then
        clusteringRule.createTopic("foo", 1);
    }

    private List<BrokerInfo> requestTopologyAsync()
    {
        final Future<Topology> topologyResponseFuture = zeebeClient.newTopologyRequest().send();

        waitUntil(() -> topologyResponseFuture.isDone());

        try
        {
            return topologyResponseFuture.get().getBrokers();
        }
        catch (Exception e)
        {
            return null;
        }

    }
}
