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
package io.zeebe.broker.it.clustering.topology;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.PartitionBrokerRole;
import io.zeebe.client.api.commands.PartitionInfo;
import io.zeebe.client.api.commands.Topology;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class TopologyClusterSmallerReplicationTest {

  private static final Timeout TEST_TIMEOUT = Timeout.seconds(120);
  private static final ClusteringRule CLUSTERING_RULE = new ClusteringRule(3, 2, 3);
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(CLUSTERING_RULE);

  @ClassRule
  public static final RuleChain RULE_CHAIN =
      RuleChain.outerRule(TEST_TIMEOUT).around(CLUSTERING_RULE).around(CLIENT_RULE);

  @Test
  public void shouldHaveCorrectReplicationFactorForPartitions() {
    // when
    final Topology topology = CLIENT_RULE.getClient().newTopologyRequest().send().join();

    // then
    final List<BrokerInfo> brokers = topology.getBrokers();

    assertThat(brokers)
        .flatExtracting(BrokerInfo::getPartitions)
        .filteredOn(PartitionInfo::isLeader)
        .extracting(PartitionInfo::getPartitionId)
        .containsExactlyInAnyOrder(
            START_PARTITION_ID, START_PARTITION_ID + 1, START_PARTITION_ID + 2);

    assertPartitionInTopology(brokers, START_PARTITION_ID);
    assertPartitionInTopology(brokers, START_PARTITION_ID + 1);
    assertPartitionInTopology(brokers, START_PARTITION_ID + 2);
  }

  private void assertPartitionInTopology(List<BrokerInfo> brokers, int partition) {
    assertThat(brokers)
        .flatExtracting(BrokerInfo::getPartitions)
        .filteredOn(p -> p.getPartitionId() == partition)
        .extracting(PartitionInfo::getRole)
        .containsExactlyInAnyOrder(PartitionBrokerRole.LEADER, PartitionBrokerRole.FOLLOWER);
  }
}
