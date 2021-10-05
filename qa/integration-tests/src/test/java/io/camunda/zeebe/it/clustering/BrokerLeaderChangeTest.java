/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.protocol.Protocol;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public final class BrokerLeaderChangeTest {
  private static final Duration SNAPSHOT_PERIOD = Duration.ofMinutes(5);

  public final Timeout testTimeout = Timeout.seconds(120);
  public final ClusteringRule clusteringRule =
      new ClusteringRule(
          1,
          3,
          3,
          config -> {
            config.getData().setSnapshotPeriod(SNAPSHOT_PERIOD);
          });
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldBecomeFollowerAfterRestart() {
    // given
    final int partition = Protocol.START_PARTITION_ID;
    final int oldLeader = clusteringRule.getLeaderForPartition(partition).getNodeId();
    clusteringRule.stopBrokerAndAwaitNewLeader(oldLeader);

    // when
    clusteringRule.startBroker(oldLeader);

    // then
    final Stream<PartitionInfo> partitionInfo =
        clusteringRule.getTopologyFromClient().getBrokers().stream()
            .filter(b -> b.getNodeId() == oldLeader)
            .flatMap(b -> b.getPartitions().stream().filter(p -> p.getPartitionId() == partition));

    assertThat(partitionInfo).noneMatch(PartitionInfo::isLeader);
  }

  @Test
  public void shouldBeAbleToBecomeLeaderAgain() {
    // given
    final var firstLeaderInfo = clusteringRule.getLeaderForPartition(1);
    final var firstLeaderNodeId = firstLeaderInfo.getNodeId();

    // when
    clusteringRule.forceClusterToHaveNewLeader(firstLeaderNodeId);

    // then
    assertThat(clusteringRule.getCurrentLeaderForPartition(1).getNodeId())
        .isEqualTo(firstLeaderNodeId);
  }
}
