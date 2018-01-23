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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.TopicLeader;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

/**
 *
 */
public class GossipClusteringTest
{
    private static final int PARTITION_COUNT = 5;

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


    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private ZeebeClient client;

    @Before
    public void startUp()
    {
        client = clientRule.getClient();
    }

    @Test
    public void shouldStartCluster()
    {
        // given

        // when

        // then wait until cluster is ready
        final List<SocketAddress> topologyBrokers = clusteringRule.waitForExactBrokerCount(3);

        assertThat(topologyBrokers).containsExactlyInAnyOrder(new SocketAddress("localhost", 51015),
                                                              new SocketAddress("localhost", 41015),
                                                              new SocketAddress("localhost", 31015));
    }

    @Test
    public void shouldDistributePartitionsAndLeaderInformationInCluster()
    {
        // given
        clusteringRule.waitForExactBrokerCount(3);

        // when
        client.topics().create("test", PARTITION_COUNT).execute();

        // then
        final List<TopicLeader> topicLeaders = clusteringRule.waitForGreaterOrEqualLeaderCount(PARTITION_COUNT + 1);
        final long partitionLeaderCount = topicLeaders.stream()
                                                      .filter((leader) -> leader.getTopicName().equals("test"))
                                                      .count();
        assertThat(partitionLeaderCount).isEqualTo(PARTITION_COUNT);
    }

    @Test
    public void shouldRemoveMemberFromTopology()
    {
        // given
        clusteringRule.waitForExactBrokerCount(3);

        // when
        clusteringRule.stopBroker(2);

        // then
        final List<SocketAddress> topologyBrokers = clusteringRule.waitForExactBrokerCount(2);

        assertThat(topologyBrokers).containsExactlyInAnyOrder(new SocketAddress("localhost", 51015),
                                                              new SocketAddress("localhost", 41015));
    }

    @Test
    public void shouldRemoveLeaderFromCluster()
    {
        // given
        clusteringRule.waitForExactBrokerCount(3);

        // when
        clusteringRule.stopBroker(0);

        // then
        final List<SocketAddress> topologyBrokers = clusteringRule.waitForExactBrokerCount(2);

        assertThat(topologyBrokers).containsExactlyInAnyOrder(new SocketAddress("localhost", 31015),
                                                              new SocketAddress("localhost", 41015));
    }

    @Test
    public void shouldReAddToCluster()
    {
        // given
        clusteringRule.waitForExactBrokerCount(3);
        clusteringRule.stopBroker(2);
        clusteringRule.waitForExactBrokerCount(2);

        // when
        clusteringRule.startBroker(2);

        // then
        final List<SocketAddress> topologyBrokers = clusteringRule.waitForExactBrokerCount(3);

        assertThat(topologyBrokers).containsExactlyInAnyOrder(new SocketAddress("localhost", 51015),
                                                              new SocketAddress("localhost", 41015),
                                                              new SocketAddress("localhost", 31015));
    }
}
