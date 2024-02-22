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
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.bootstrap.BrokerContext;
import io.camunda.zeebe.broker.system.management.BrokerAdminService;
import io.camunda.zeebe.it.clustering.ClusteringRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BrokerAdminServiceClusterTest {

  private static final int PARTITION_ID = 1;
  private final Timeout testTimeout = Timeout.seconds(60);
  private final ClusteringRule clusteringRule =
      new ClusteringRule(
          1,
          3,
          3,
          cfg -> {
            cfg.getData().setLogIndexDensity(1);
            cfg.getData().setSnapshotPeriod(Duration.ofMinutes(15));
          });
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
  public void shouldReportPartitionStatusOnFollowersAndLeader() {
    // given
    final var followers =
        clusteringRule.getOtherBrokerObjects(
            clusteringRule.getLeaderForPartition(PARTITION_ID).getNodeId());

    // when
    final var followerStatus =
        followers.stream()
            .map(Broker::getBrokerContext)
            .map(BrokerContext::getBrokerAdminService)
            .map(BrokerAdminService::getPartitionStatus)
            .map(status -> status.get(1));

    final var leaderStatus = leaderAdminService.getPartitionStatus().get(PARTITION_ID);

    // then
    followerStatus.forEach(
        partitionStatus -> {
          assertThat(partitionStatus.role()).isEqualTo(Role.FOLLOWER);
          assertThat(partitionStatus.processedPosition()).isEqualTo(-1L);
          assertThat(partitionStatus.snapshotId()).isNull();
          assertThat(partitionStatus.processedPositionInSnapshot()).isNull();
          assertThat(partitionStatus.streamProcessorPhase()).isEqualTo(Phase.REPLAY);
        });

    assertThat(leaderStatus.role()).isEqualTo(Role.LEADER);
    assertThat(leaderStatus.processedPosition()).isEqualTo(-1);
    assertThat(leaderStatus.snapshotId()).isNull();
    assertThat(leaderStatus.processedPositionInSnapshot()).isNull();
    assertThat(leaderStatus.streamProcessorPhase()).isEqualTo(Phase.PROCESSING);
  }

  @Test
  public void shouldReportPartitionStatusWithSnapshotOnFollowers() {
    // given
    clientRule.createSingleJob("test");

    // when
    clusteringRule.triggerAndWaitForSnapshots();

    // then
    clusteringRule.getBrokers().stream()
        .map(Broker::getBrokerContext)
        .map(BrokerContext::getBrokerAdminService)
        .forEach(this::assertThatStatusContainsProcessedPositionInSnapshot);
  }

  @Test
  public void shouldPauseAfterLeaderChange() {
    // given
    clusteringRule.getBrokers().stream()
        .map(Broker::getBrokerContext)
        .map(BrokerContext::getBrokerAdminService)
        .forEach(BrokerAdminService::pauseStreamProcessing);

    // when
    assertStreamProcessorPhase(leaderAdminService, Phase.PAUSED);
    clusteringRule.stopBrokerAndAwaitNewLeader(leader.getConfig().getCluster().getNodeId());

    // then
    final var newLeaderAdminService =
        clusteringRule
            .getBroker(clusteringRule.getLeaderForPartition(1).getNodeId())
            .getBrokerContext()
            .getBrokerAdminService();
    assertStreamProcessorPhase(newLeaderAdminService, Phase.PAUSED);
  }

  private void assertThatStatusContainsProcessedPositionInSnapshot(
      final BrokerAdminService adminService) {
    adminService
        .getPartitionStatus()
        .values()
        .forEach(
            status ->
                assertThat(status.processedPositionInSnapshot())
                    .describedAs(status.toString())
                    .isNotNull());
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
}
