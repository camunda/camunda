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

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.transport.SocketAddress;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class GossipClusteringTest {
  private static final int PARTITION_COUNT = 3;

  public Timeout testTimeout = Timeout.seconds(120);
  public ClusteringRule clusteringRule = new ClusteringRule();
  public GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldStartCluster() {
    // given

    // when
    final List<SocketAddress> topologyBrokers = clusteringRule.getBrokersInCluster();

    // then
    assertThat(topologyBrokers).hasSize(3);
  }

  @Test
  public void shouldDistributePartitionsAndLeaderInformationInCluster() {
    // then
    final long partitionLeaderCount = clusteringRule.getPartitionLeaderCount();
    assertThat(partitionLeaderCount).isEqualTo(PARTITION_COUNT);
  }

  @Test
  public void shouldRemoveMemberFromTopology() {
    // given
    final SocketAddress[] otherBrokers = clusteringRule.getOtherBrokers(2);

    // when
    clusteringRule.stopBroker(2);

    // then
    final List<SocketAddress> topologyBrokers = clusteringRule.getBrokersInCluster();

    assertThat(topologyBrokers).containsExactlyInAnyOrder(otherBrokers);
  }

  @Test
  public void shouldRemoveLeaderFromCluster() {
    // given
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(1);
    final SocketAddress[] otherBrokers =
        clusteringRule.getOtherBrokers(leaderForPartition.getAddress());

    // when
    clusteringRule.stopBroker(leaderForPartition.getNodeId());

    // then
    final List<SocketAddress> topologyBrokers = clusteringRule.getBrokersInCluster();

    assertThat(topologyBrokers).containsExactlyInAnyOrder(otherBrokers);
  }

  @Test
  public void shouldReAddToCluster() {
    // when
    clusteringRule.restartBroker(2);

    // then
    final List<SocketAddress> topologyBrokers = clusteringRule.getBrokersInCluster();

    assertThat(topologyBrokers).hasSize(3);
  }
}
