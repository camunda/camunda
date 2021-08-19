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
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor.Phase;
import io.camunda.zeebe.it.clustering.ClusteringRule;
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
    final var leaderAdminService = leader.getBrokerAdminService();
    // when there are no exporters configured
    // then
    final var partitionStatus = leaderAdminService.getPartitionStatus().get(1);
    assertThat(partitionStatus.getRole()).isEqualTo(Role.LEADER);
    assertThat(partitionStatus.getProcessedPosition()).isEqualTo(-1);
    assertThat(partitionStatus.getSnapshotId()).isNull();
    assertThat(partitionStatus.getProcessedPositionInSnapshot()).isNull();
    assertThat(partitionStatus.getStreamProcessorPhase()).isEqualTo(Phase.PROCESSING);
    assertThat(partitionStatus.getExporterPhase()).isEqualTo(ExporterPhase.CLOSED);
    assertThat(partitionStatus.getExportedPosition()).isEqualTo(-1);
  }
}
