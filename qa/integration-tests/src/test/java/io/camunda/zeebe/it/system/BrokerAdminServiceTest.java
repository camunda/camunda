/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.system;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.exporter.stream.ExporterPhase;
import io.camunda.zeebe.broker.system.management.BrokerAdminService;
import io.camunda.zeebe.it.clustering.ClusteringRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BrokerAdminServiceTest {
  private final Timeout testTimeout = Timeout.seconds(60);
  private final ClusteringRule clusteringRule =
      new ClusteringRule(1, 1, 1, cfg -> cfg.getData().setLogIndexDensity(1));
  private final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  private BrokerAdminService leaderAdminService;
  private Broker leader;

  @Before
  public void before() {
    leader = clusteringRule.getBroker(clusteringRule.getLeaderForPartition(1).getNodeId());
    leaderAdminService = leader.getBrokerContext().getBrokerAdminService();
  }

  @Test
  public void shouldTakeSnapshotWhenRequested() {
    // given
    clientRule.createSingleJob("test");

    // when
    leaderAdminService.takeSnapshot();

    // then
    waitForSnapshotAtBroker(leaderAdminService);
  }

  @Test
  public void shouldPauseStreamProcessorWhenRequested() {
    // given
    clientRule.createSingleJob("test");

    // when
    leaderAdminService.pauseStreamProcessing();

    // then
    assertStreamProcessorPhase(leaderAdminService, Phase.PAUSED);
  }

  @Test
  public void shouldResumeStreamProcessorWhenRequested() {

    // when
    leaderAdminService.pauseStreamProcessing();
    assertStreamProcessorPhase(leaderAdminService, Phase.PAUSED);
    leaderAdminService.resumeStreamProcessing();

    // then
    assertStreamProcessorPhase(leaderAdminService, Phase.PROCESSING);
  }

  @Test
  public void shouldPauseExporterWhenRequested() {
    // when
    leaderAdminService.pauseExporting();

    // then
    assertExporterPhase(leaderAdminService, ExporterPhase.PAUSED);
  }

  @Test
  public void shouldResumeExportingWhenRequested() {
    // given
    leaderAdminService.pauseExporting();
    assertExporterPhase(leaderAdminService, ExporterPhase.PAUSED);

    // when
    final String messageName = "test";
    clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName(messageName)
        .correlationKey("test-key")
        .send()
        .join();
    leaderAdminService.resumeExporting();

    // then
    assertExporterPhase(leaderAdminService, ExporterPhase.EXPORTING);
    Awaitility.await()
        .timeout(Duration.ofSeconds(60))
        .until(
            () ->
                RecordingExporter.messageRecords(MessageIntent.PUBLISHED)
                    .withName(messageName)
                    .exists());
  }

  @Test
  public void shouldPauseStreamProcessorAndExporterAndTakeSnapshotWhenPrepareUgrade() {
    // given
    clientRule.createSingleJob("test");

    // when
    leaderAdminService.prepareForUpgrade();

    // then
    waitForSnapshotAtBroker(leaderAdminService);

    assertStreamProcessorPhase(leaderAdminService, Phase.PAUSED);
    assertExporterPhase(leaderAdminService, ExporterPhase.PAUSED);
    assertProcessedPositionIsInSnapshot(leaderAdminService);
  }

  @Test
  public void shouldPauseStreamProcessorAfterRestart() {
    // given
    leaderAdminService.pauseStreamProcessing();
    assertStreamProcessorPhase(leaderAdminService, Phase.PAUSED);

    // when
    clusteringRule.restartCluster();

    // then
    leader = clusteringRule.getBroker(clusteringRule.getLeaderForPartition(1).getNodeId());
    leaderAdminService = leader.getBrokerContext().getBrokerAdminService();
    assertStreamProcessorPhase(leaderAdminService, Phase.PAUSED);
  }

  @Test
  public void shouldResumeStreamProcessorAfterRestart() {
    // given
    leaderAdminService.pauseStreamProcessing();
    assertStreamProcessorPhase(leaderAdminService, Phase.PAUSED);
    leaderAdminService.resumeStreamProcessing();
    assertStreamProcessorPhase(leaderAdminService, Phase.PROCESSING);

    // when
    clusteringRule.restartCluster();

    // then
    leader = clusteringRule.getBroker(clusteringRule.getLeaderForPartition(1).getNodeId());
    leaderAdminService = leader.getBrokerContext().getBrokerAdminService();
    assertStreamProcessorPhase(leaderAdminService, Phase.PROCESSING);
  }

  @Test
  public void shouldPauseExporterAfterRestart() {
    // given
    leaderAdminService.pauseExporting();
    assertExporterPhase(leaderAdminService, ExporterPhase.PAUSED);

    // when
    clusteringRule.restartCluster();

    // then
    leader = clusteringRule.getBroker(clusteringRule.getLeaderForPartition(1).getNodeId());
    leaderAdminService = leader.getBrokerContext().getBrokerAdminService();
    assertExporterPhase(leaderAdminService, ExporterPhase.PAUSED);
  }

  @Test
  public void shouldResumeExporterAfterRestart() {
    // given
    leaderAdminService.pauseExporting();
    assertExporterPhase(leaderAdminService, ExporterPhase.PAUSED);
    leaderAdminService.resumeExporting();
    assertExporterPhase(leaderAdminService, ExporterPhase.EXPORTING);

    // when
    clusteringRule.restartCluster();

    // then
    leader = clusteringRule.getBroker(clusteringRule.getLeaderForPartition(1).getNodeId());
    leaderAdminService = leader.getBrokerContext().getBrokerAdminService();
    assertExporterPhase(leaderAdminService, ExporterPhase.EXPORTING);
  }

  private void assertStreamProcessorPhase(
      final BrokerAdminService brokerAdminService, final Phase expected) {
    Awaitility.await()
        .untilAsserted(
            () ->
                brokerAdminService
                    .getPartitionStatus()
                    .forEach(
                        (p, status) ->
                            assertThat(status.streamProcessorPhase()).isEqualTo(expected)));
  }

  private void assertExporterPhase(
      final BrokerAdminService brokerAdminService, final ExporterPhase expected) {
    Awaitility.await()
        .untilAsserted(
            () ->
                brokerAdminService
                    .getPartitionStatus()
                    .forEach(
                        (p, status) -> assertThat(status.exporterPhase()).isEqualTo(expected)));
  }

  private void assertProcessedPositionIsInSnapshot(final BrokerAdminService brokerAdminService) {
    Awaitility.await()
        .untilAsserted(
            () ->
                brokerAdminService
                    .getPartitionStatus()
                    .forEach(
                        (p, status) ->
                            assertThat(status.processedPosition())
                                .isEqualTo(status.processedPositionInSnapshot())));
  }

  private void waitForSnapshotAtBroker(final BrokerAdminService adminService) {
    Awaitility.await()
        .untilAsserted(
            () ->
                adminService
                    .getPartitionStatus()
                    .values()
                    .forEach(
                        status -> assertThat(status.processedPositionInSnapshot()).isNotNull()));
  }
}
