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

import static io.zeebe.broker.it.clustering.ClusteringRule.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.SocketAddress;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class GossipDifferentNodeJoinTest {
  private static final int PARTITION_COUNT = 3;

  public AutoCloseableRule closeables = new AutoCloseableRule();
  public Timeout testTimeout = Timeout.seconds(30);
  public ClientRule clientRule = new ClientRule();

  private SocketAddress[] brokerAddresses =
      new SocketAddress[] {
        BROKER_1_CLIENT_ADDRESS, BROKER_2_CLIENT_ADDRESS, BROKER_4_CLIENT_ADDRESS
      };
  private String[] brokerConfigs = new String[] {BROKER_1_TOML, BROKER_2_TOML, BROKER_4_TOML};

  public ClusteringRule clusteringRule =
      new ClusteringRule(closeables, clientRule, brokerAddresses, brokerConfigs);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(closeables).around(testTimeout).around(clientRule).around(clusteringRule);

  @Test
  public void shouldStartCluster() {
    // given

    // when
    final List<SocketAddress> topologyBrokers = clusteringRule.getBrokersInCluster();

    // then
    assertThat(topologyBrokers)
        .containsExactlyInAnyOrder(
            ClusteringRule.BROKER_1_CLIENT_ADDRESS,
            ClusteringRule.BROKER_2_CLIENT_ADDRESS,
            ClusteringRule.BROKER_4_CLIENT_ADDRESS);
  }

  @Test
  public void shouldDistributePartitionsAndLeaderInformationInCluster() {
    // given

    // when
    clusteringRule.createTopic("test", PARTITION_COUNT);

    // then
    final long partitionLeaderCount = clusteringRule.getPartitionLeaderCountForTopic("test");
    assertThat(partitionLeaderCount).isEqualTo(PARTITION_COUNT);
  }

  @Test
  public void shouldRemoveMemberFromTopology() {
    // given
    final SocketAddress brokerAddress = ClusteringRule.BROKER_4_CLIENT_ADDRESS;
    final SocketAddress[] otherBrokers = clusteringRule.getOtherBrokers(brokerAddress);

    // when
    clusteringRule.stopBroker(brokerAddress);

    // then
    final List<SocketAddress> topologyBrokers = clusteringRule.getBrokersInCluster();

    assertThat(topologyBrokers).containsExactlyInAnyOrder(otherBrokers);
  }

  @Test
  public void shouldRemoveLeaderFromCluster() {
    // given
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(0);
    final SocketAddress[] otherBrokers =
        clusteringRule.getOtherBrokers(leaderForPartition.getAddress());

    // when
    clusteringRule.stopBroker(leaderForPartition.getAddress());

    // then
    final List<SocketAddress> topologyBrokers = clusteringRule.getBrokersInCluster();

    assertThat(topologyBrokers).containsExactlyInAnyOrder(otherBrokers);
  }

  @Test
  public void shouldReAddToCluster() {
    // given
    clusteringRule.stopBroker(ClusteringRule.BROKER_4_CLIENT_ADDRESS);

    // when
    clusteringRule.restartBroker(ClusteringRule.BROKER_4_CLIENT_ADDRESS);

    // then
    final List<SocketAddress> topologyBrokers = clusteringRule.getBrokersInCluster();

    assertThat(topologyBrokers)
        .containsExactlyInAnyOrder(
            ClusteringRule.BROKER_1_CLIENT_ADDRESS,
            ClusteringRule.BROKER_2_CLIENT_ADDRESS,
            ClusteringRule.BROKER_4_CLIENT_ADDRESS);
  }
}
