/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering.topology;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.client.api.response.BrokerInfo;
import io.zeebe.client.api.response.PartitionBrokerRole;
import io.zeebe.client.api.response.PartitionInfo;
import io.zeebe.client.api.response.Topology;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public final class TopologyClusterSmallerReplicationTest {

  private static final Timeout TEST_TIMEOUT = Timeout.seconds(120);
  private static final ClusteringRule CLUSTERING_RULE =
      new ClusteringRule(3, 2, 3, cfg -> cfg.getData().setUseMmap(false));
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

  private void assertPartitionInTopology(final List<BrokerInfo> brokers, final int partition) {
    assertThat(brokers)
        .flatExtracting(BrokerInfo::getPartitions)
        .filteredOn(p -> p.getPartitionId() == partition)
        .extracting(PartitionInfo::getRole)
        .containsExactlyInAnyOrder(PartitionBrokerRole.LEADER, PartitionBrokerRole.FOLLOWER);
  }
}
