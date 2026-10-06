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
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.nio.file.Path;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that an in-process restore of a backup taken with fewer partitions than the cluster's
 * dynamic configuration holds routes the cluster over the backup's partitions only.
 *
 * <p>The backup is taken with {@value #BACKUP_PARTITIONS_COUNT} partitions, then the cluster is
 * scaled up to {@value #SCALED_PARTITIONS_COUNT}. The restore must not reject the backup over the
 * mismatch: the partition count comes from the backup, and the restore plan's routing state update
 * is gossiped to every broker. That is checked per broker rather than through the cluster endpoint,
 * which only reports the coordinator's view: every broker's embedded gateway routes new process
 * instances with its own copy of the routing state, so each must place them on the backup's
 * partitions.
 */
@ZeebeIntegration
final class RestoreScaledUpPartitionsBackIT {

  private static final int BROKERS_COUNT = 2;
  private static final int BACKUP_PARTITIONS_COUNT = 2;
  private static final int SCALED_PARTITIONS_COUNT = 3;
  private static final long BACKUP_ID = 7;
  private static final String PROCESS_ID = "fewer-partitions-process";
  private static final String JOB_TYPE = "fewer-partitions-job";

  @TempDir private Path backupDir;

  @Test
  void shouldRouteOverTheBackupsPartitionsOnEveryBrokerAfterRestore() {
    try (final var cluster =
            TestCluster.builder()
                .withBrokersCount(BROKERS_COUNT)
                .withPartitionsCount(BACKUP_PARTITIONS_COUNT)
                .withReplicationFactor(BROKERS_COUNT)
                .withEmbeddedGateway(true)
                .withBrokerConfig(broker -> configureBroker(broker.unifiedConfig()))
                .build()
                .start()
                .awaitCompleteTopology();
        final var client = cluster.newClientBuilder().build()) {

      // given - a backup with a pending job on each of its partitions
      final var backedUpInstanceKeys =
          InProcessRestoreTestUtil.deployAndCreateInstancesOnEveryPartition(
              client, PROCESS_ID, JOB_TYPE, BACKUP_PARTITIONS_COUNT);
      takeSnapshotOnAllBrokers(cluster);
      takeBackup(BackupActuator.of(cluster.availableGateway()));

      // and - the cluster is scaled up past the backup's partition count
      final var clusterActuator = ClusterActuator.of(cluster.availableGateway());
      InProcessRestoreTestUtil.scaleUpPartitions(
          clusterActuator, SCALED_PARTITIONS_COUNT, BROKERS_COUNT);

      final var toRecovering = InProcessRestoreTestUtil.changeMode(client, "RECOVERING", false);
      Awaitility.await("cluster transitions to RECOVERING")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(clusterActuator)
                      .hasCompletedChanges(toRecovering)
                      .doesNotHavePendingChanges());

      // when
      final var restore = InProcessRestoreTestUtil.triggerRestore(client, BACKUP_ID);

      // then - the restore completes despite the partition count mismatch
      Awaitility.await("restore change plan completes")
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(clusterActuator)
                      .hasCompletedChanges(restore)
                      .doesNotHavePendingChanges());
      InProcessRestoreTestUtil.assertRoutesOverPartitions(clusterActuator, BACKUP_PARTITIONS_COUNT);

      // and - every broker routes new instances over the backup's partitions only
      cluster
          .brokers()
          .values()
          .forEach(
              broker ->
                  InProcessRestoreTestUtil.assertNewInstancesLandOnPartitions(
                      broker, PROCESS_ID, BACKUP_PARTITIONS_COUNT));

      // and - the state from the backup is restored
      InProcessRestoreTestUtil.awaitJobsOfInstancesActivatable(
          client, JOB_TYPE, backedUpInstanceKeys);
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

  private void takeBackup(final BackupActuator actuator) {
    assertThat(actuator.take(BACKUP_ID)).isInstanceOf(TakeBackupRuntimeResponse.class);
    Awaitility.await("until a backup exists with the given ID")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // 404 NOT_FOUND throws exception
        .untilAsserted(
            () ->
                assertThat(actuator.status(BACKUP_ID))
                    .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                    .containsExactly(BACKUP_ID, StateCode.COMPLETED));
  }

  /** Backup store can only be configured via UnifiedConfiguration */
  private void configureBroker(final Camunda cfg) {
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(backupDir.toAbsolutePath().toString());

    // Faster gossip so the routing state written by the restore plan reaches every broker quickly
    cfg.getCluster().getMembership().setSyncInterval(Duration.ofSeconds(1));
    cfg.getCluster().getMembership().setGossipInterval(Duration.ofMillis(500));
  }
}
