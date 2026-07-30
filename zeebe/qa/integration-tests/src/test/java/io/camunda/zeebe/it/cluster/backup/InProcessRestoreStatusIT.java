/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.protocol.rest.RestorePartitionStatus;
import io.camunda.client.protocol.rest.RestorePartitionStatus.StateEnum;
import io.camunda.client.protocol.rest.RestoreStatusResponse;
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
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that {@code GET v2/restore} reports a single broker's copy of a partition progressing
 * through {@code RESTORING} to {@code RESTORED}.
 *
 * <p>The cluster change plan that drives a restore executes strictly sequentially, one operation at
 * a time across the whole cluster; every partition's {@code PartitionPreRestoreOperation} runs
 * before any {@code PartitionRestoreOperation} starts. This test targets broker {@value
 * #TARGET_BROKER_ID}'s copy of partition {@value #TARGET_PARTITION_ID} - the last operation in that
 * sequence for a {@value #BROKERS_COUNT}-broker, replication-factor-{@value #BROKERS_COUNT} cluster
 * - and corrupts only that broker's on-disk backup snapshot for that partition, using the same
 * technique as {@link InProcessRestoreRetryOnCorruptionIT}. This makes {@code RESTORING} and {@code
 * RESTORED} fully deterministic: the corrupted step fails and retries with a real backoff until the
 * corruption is fixed, so the intermediate state is held open for a known window rather than raced
 * against.
 *
 * <p>{@code PENDING} is not asserted here: with only one {@code PartitionPreRestoreOperation}
 * preceding the target in this small cluster's operation queue, the target flips to {@code
 * RESTORING} before it can be reliably observed. The full {@code PENDING -> RESTORING -> RESTORED}
 * sequence is covered deterministically at the unit level in {@code RestoreStatusTest}.
 */
@ZeebeIntegration
final class InProcessRestoreStatusIT {

  private static final int BROKERS_COUNT = 2;
  private static final int PARTITIONS_COUNT = 2;
  private static final long BACKUP_ID = 42;
  private static final String JOB_TYPE = "restore-status-job";
  private static final String PROCESS_ID = "restore-status-process";
  private static final int TARGET_BROKER_ID = 1;
  private static final int TARGET_PARTITION_ID = 2;

  @TempDir private Path backupDir;

  @Test
  void shouldReportPartitionRestoreStatusThroughRestoringAndRestored() throws IOException {
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

      // given
      InProcessRestoreTestUtil.deployAndCreateInstancesOnEveryPartition(
          client, PROCESS_ID, JOB_TYPE, PARTITIONS_COUNT);
      takeSnapshotOnAllBrokers(cluster);
      takeBackup(BackupActuator.of(cluster.availableGateway()));
      final var originalSnapshotFiles =
          InProcessRestoreTestUtil.corruptPartitionSnapshot(
              backupDir, BACKUP_ID, TARGET_BROKER_ID, TARGET_PARTITION_ID);

      final var clusterActuator = ClusterActuator.of(cluster.availableGateway());
      final var toRecovering = InProcessRestoreTestUtil.changeMode(client, "RECOVERING", false);
      Awaitility.await("cluster transitions to RECOVERING")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(clusterActuator)
                      .hasCompletedChanges(toRecovering)
                      .doesNotHavePendingChanges());

      // when
      final var changeId = InProcessRestoreTestUtil.triggerRestore(client, BACKUP_ID);

      // then
      Awaitility.await("target partition's restore step is the one stuck retrying")
          .timeout(Duration.ofSeconds(30))
          .untilAsserted(() -> assertTargetPartitionState(client, StateEnum.RESTORING));

      Awaitility.await("the corrupted restore keeps reporting restoring across multiple retries")
          .during(Duration.ofSeconds(25))
          .atMost(Duration.ofSeconds(35))
          .until(
              () -> {
                assertTargetPartitionState(client, StateEnum.RESTORING);
                return true;
              });

      InProcessRestoreTestUtil.restoreOriginalSnapshotFiles(originalSnapshotFiles);

      Awaitility.await("target partition is reported restored once the retry succeeds")
          .timeout(Duration.ofSeconds(30))
          .untilAsserted(() -> assertTargetPartitionState(client, StateEnum.RESTORED));

      Awaitility.await("restore change plan completes")
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(clusterActuator)
                      .hasCompletedChanges(changeId)
                      .doesNotHavePendingChanges());
    }
  }

  private void assertTargetPartitionState(final CamundaClient client, final StateEnum expected) {
    final var status = InProcessRestoreTestUtil.getRestoreStatus(client);
    assertThat(findTargetPartitionStatus(status).getState()).isEqualTo(expected);
  }

  private RestorePartitionStatus findTargetPartitionStatus(final RestoreStatusResponse status) {
    return status.getBrokers().stream()
        .filter(broker -> broker.getBrokerId().equals(String.valueOf(TARGET_BROKER_ID)))
        .flatMap(broker -> broker.getPartitions().stream())
        .filter(partition -> partition.getPartitionId() == TARGET_PARTITION_ID)
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "no restore status reported for broker %d partition %d"
                        .formatted(TARGET_BROKER_ID, TARGET_PARTITION_ID)));
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
            () -> {
              final var status = actuator.status(BACKUP_ID);
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly(BACKUP_ID, StateCode.COMPLETED);
            });
  }

  /** Backup store can only be configured via UnifiedConfiguration */
  private void configureBackupStore(final Camunda cfg) {
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    backup.setStore(BackupStoreType.FILESYSTEM);

    final var config = backup.getFilesystem();
    config.setBasePath(backupDir.toAbsolutePath().toString());
    backup.setFilesystem(config);
  }
}
