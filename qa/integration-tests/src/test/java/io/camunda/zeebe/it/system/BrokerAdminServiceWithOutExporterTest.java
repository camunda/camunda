/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.system;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.exporter.stream.ExporterPhase;
import io.camunda.zeebe.it.clustering.ClusteringRule;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BrokerAdminServiceWithOutExporterTest {
  private final Timeout testTimeout = Timeout.seconds(60);
  private final ClusteringRule clusteringRule =
      new ClusteringRule(1, 1, 1, cfg -> cfg.getExporters().clear());

  @Rule public RuleChain ruleChain = RuleChain.outerRule(testTimeout).around(clusteringRule);

  @Test
  public void shouldReportStatusWhenNoExporters() {
    // given
    final var leader =
        clusteringRule.getBroker(clusteringRule.getLeaderForPartition(1).getNodeId());
    final var leaderAdminService = leader.getBrokerContext().getBrokerAdminService();
    // when there are no exporters configured
    // then
    final var partitionStatus = leaderAdminService.getPartitionStatus().get(1);
    assertThat(partitionStatus.role()).isEqualTo(Role.LEADER);
    assertThat(partitionStatus.processedPosition()).isEqualTo(-1);
    assertThat(partitionStatus.snapshotId()).isNull();
    assertThat(partitionStatus.processedPositionInSnapshot()).isNull();
    assertThat(partitionStatus.streamProcessorPhase()).isEqualTo(Phase.PROCESSING);
    assertThat(partitionStatus.exporterPhase()).isEqualTo(ExporterPhase.CLOSED);
    assertThat(partitionStatus.exportedPosition()).isEqualTo(-1);
  }
}
