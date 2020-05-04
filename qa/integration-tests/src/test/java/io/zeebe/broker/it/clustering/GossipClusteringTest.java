/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.client.api.response.BrokerInfo;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public final class GossipClusteringTest {

  public final Timeout testTimeout = Timeout.seconds(120);
  public final ClusteringRule clusteringRule =
      new ClusteringRule(1, 3, 3, cfg -> cfg.getData().setUseMmap(false));
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldStartCluster() {
    // given

    // when
    final List<InetSocketAddress> topologyBrokers = clusteringRule.getBrokersInCluster();

    // then
    assertThat(topologyBrokers).hasSize(3);
  }

  @Test
  public void shouldDistributePartitionsAndLeaderInformationInCluster() {
    // then
    final long partitionLeaderCount = clusteringRule.getPartitionLeaderCount();
    assertThat(partitionLeaderCount).isEqualTo(1);
  }

  @Test
  public void shouldRemoveMemberFromTopology() {
    // given
    final InetSocketAddress[] otherBrokers = clusteringRule.getOtherBrokers(2);

    // when
    clusteringRule.stopBroker(2);

    // then
    final List<InetSocketAddress> topologyBrokers = clusteringRule.getBrokersInCluster();

    assertThat(topologyBrokers).containsExactlyInAnyOrder(otherBrokers);
  }

  @Test
  public void shouldRemoveLeaderFromCluster() {
    // given
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(1);
    final InetSocketAddress[] otherBrokers =
        clusteringRule.getOtherBrokers(leaderForPartition.getNodeId());

    // when
    clusteringRule.stopBroker(leaderForPartition.getNodeId());

    // then
    final List<InetSocketAddress> topologyBrokers = clusteringRule.getBrokersInCluster();

    assertThat(topologyBrokers).containsExactlyInAnyOrder(otherBrokers);
  }

  @Test
  public void shouldReAddToCluster() {
    // when
    clusteringRule.restartBroker(2);

    // then
    final List<InetSocketAddress> topologyBrokers = clusteringRule.getBrokersInCluster();

    assertThat(topologyBrokers).hasSize(3);
  }
}
