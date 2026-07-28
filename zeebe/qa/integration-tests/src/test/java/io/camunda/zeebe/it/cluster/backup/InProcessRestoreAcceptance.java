/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Acceptance test for restoring a partition in-place ("in-process restore") while a running cluster
 * is in {@code RECOVERING} mode, as opposed to {@link RestoreAcceptance} which restores via a
 * separate, standalone restore application.
 */
public interface InProcessRestoreAcceptance {

  int BROKERS_COUNT = 3;
  int PARTITIONS_COUNT = 3;
  long BACKUP_ID = 42;
  String JOB_TYPE = "in-process-restore-job";
  String PROCESS_ID = "in-process-restore-process";

  @Test
  default void shouldRestoreClusterInProcess() {
    try (final var cluster =
            TestCluster.builder()
                .withBrokersCount(BROKERS_COUNT)
                .withPartitionsCount(PARTITIONS_COUNT)
                .withReplicationFactor(BROKERS_COUNT)
                .withEmbeddedGateway(true)
                .withBrokerConfig(broker -> configureBackupStore(broker.unifiedConfig()))
                .build()
                .start()
                .awaitCompleteTopology();
        final var client = cluster.newClientBuilder().build()) {

      // given -- every partition has at least one process instance with a pending job
      InProcessRestoreTestUtil.deployAndCreateInstancesOnEveryPartition(
          client, PROCESS_ID, JOB_TYPE, PARTITIONS_COUNT);

      // and -- a completed backup, taken after a snapshot on every broker
      takeSnapshotOnAllBrokers(cluster);
      final var backupActuator = BackupActuator.of(cluster.availableGateway());
      takeBackup(backupActuator, BACKUP_ID);

      // when -- the cluster is put into RECOVERING mode over the cluster's REST endpoint
      final var clusterActuator = ClusterActuator.of(cluster.availableGateway());
      final var toRecovering = InProcessRestoreTestUtil.changeMode(client, "RECOVERING", false);
      Awaitility.await("cluster transitions to RECOVERING")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(clusterActuator)
                      .hasCompletedChanges(toRecovering)
                      .doesNotHavePendingChanges());

      // and -- a restore is triggered over the cluster's REST endpoint while recovering
      final var changeId = InProcessRestoreTestUtil.triggerRestore(client, BACKUP_ID);

      // then -- the restore change plan completes
      Awaitility.await("restore change plan completes")
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(clusterActuator)
                      .hasCompletedChanges(changeId)
                      .doesNotHavePendingChanges());

      // and -- every partition is ACTIVE again on every broker
      Awaitility.await("every partition reports ACTIVE again")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () -> {
                final var topology = clusterActuator.getTopology();
                assertThat(topology.getBrokers())
                    .flatExtracting(BrokerState::getPartitions)
                    .extracting(PartitionState::getState)
                    .allMatch(state -> state == PartitionStateCode.ACTIVE);
              });

      // and -- jobs from every partition are activated and completed again, proving partition data
      // was restored (not just topology/mode) and the spawned processes can run to completion
      InProcessRestoreTestUtil.activateAndCompleteJobsFromEveryPartition(
          client, JOB_TYPE, PARTITIONS_COUNT);

      // and -- the cluster accepts new work again after the restore
      final var newInstance =
          client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();
      assertThat(newInstance.getProcessInstanceKey()).isPositive();
    }
  }

  private void takeSnapshotOnAllBrokers(final TestCluster cluster) {
    cluster
        .brokers()
        .values()
        .forEach(
            broker -> {
              final var partitions = PartitionsActuator.of(broker);
              partitions.takeSnapshot();
              Awaitility.await("snapshot is taken on broker " + broker.nodeId())
                  .atMost(Duration.ofSeconds(60))
                  .untilAsserted(
                      () ->
                          assertThat(partitions.query().values())
                              .allSatisfy(status -> assertThat(status.snapshotId()).isNotNull()));
            });
  }

  private void takeBackup(final BackupActuator actuator, final long backupId) {
    assertThat(actuator.take(backupId)).isInstanceOf(TakeBackupRuntimeResponse.class);
    Awaitility.await("until a backup exists with the given ID")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // 404 NOT_FOUND throws exception
        .untilAsserted(
            () -> {
              final var status = actuator.status(backupId);
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly(backupId, StateCode.COMPLETED);
            });
  }

  /** Backup store can only be configured via UnifiedConfiguration */
  void configureBackupStore(final Camunda cfg);
}
