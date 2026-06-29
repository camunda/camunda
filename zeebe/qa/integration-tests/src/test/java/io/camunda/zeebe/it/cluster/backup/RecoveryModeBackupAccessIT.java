/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Filesystem;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.PartitionBackupRange;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that read-only backup operations (state/ranges, list, status) remain accessible while
 * the cluster is in RECOVERING mode, and that the data served matches what was in the backup store
 * before the mode transition.
 */
@Testcontainers
@ZeebeIntegration
@Timeout(value = 60, unit = TimeUnit.SECONDS)
final class RecoveryModeBackupAccessIT {

  private final Path tempDir;

  @TestZeebe(initMethod = "initTestCluster")
  private TestCluster cluster;

  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  RecoveryModeBackupAccessIT(@TempDir final Path tempDir) {
    this.tempDir = tempDir;
  }

  @SuppressWarnings("unused")
  private void initTestCluster() {
    cluster =
        TestCluster.builder()
            .withBrokersCount(1)
            .withPartitionsCount(1)
            .withReplicationFactor(1)
            .withEmbeddedGateway(true)
            .withBrokerConfig(this::configureBroker)
            .build();
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private void configureBroker(final TestStandaloneBroker broker) {
    broker.withUnifiedConfig(
        cfg -> {
          cfg.getData()
              .getPrimaryStorage()
              .getBackup()
              .setStore(PrimaryStorageBackup.BackupStoreType.FILESYSTEM);

          final var fsConfig = new Filesystem();
          fsConfig.setBasePath(tempDir.toAbsolutePath().toString());
          cfg.getData().getPrimaryStorage().getBackup().setFilesystem(fsConfig);

          cfg.getData().getPrimaryStorage().getBackup().setContinuous(true);
          cfg.getData()
              .getPrimaryStorage()
              .getBackup()
              .setSchedule(Duration.ofSeconds(5).toString());
        });
  }

  @Test
  void shouldExposeBackupRangesAndListInRecoveryMode() {
    // given — continuous backups running; wait until at least one range has a distinct start and
    // end (meaning at least two sequential backups have been taken and a range was computed)
    final var backupActuator = BackupActuator.of(cluster.availableGateway());
    final var clusterActuator = ClusterActuator.of(cluster.availableGateway());

    executor.scheduleAtFixedRate(this::generateLoad, 0, 100, TimeUnit.MILLISECONDS);

    Awaitility.await("at least one backup range with distinct start and end checkpoints")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var ranges = backupActuator.state().getRanges();
              assertThat(ranges)
                  .isNotEmpty()
                  .allSatisfy(
                      range ->
                          assertThat(range.getEnd().getCheckpointId())
                              .isGreaterThan(range.getStart().getCheckpointId()));
            });

    // Flush range/checkpoint metadata from RocksDB to the backup store. This is what
    // RecoveryBackupService reads from (it has no access to the partition's RocksDB in
    // recovery mode). The return value is the state at the moment of flush — use it as the
    // ground truth for the ranges assertion below.
    final var syncedState = backupActuator.syncMetadata();
    final List<PartitionBackupRange> expectedRanges = syncedState.getRanges();

    // Capture backup list from the backup store before mode transition. Both processing and
    // recovery mode read backup entries from the same backup store, so this acts as the reference.
    final List<BackupInfo> backupsBeforeRecovery = backupActuator.list();
    assertThat(backupsBeforeRecovery).isNotEmpty();

    // when — transition the cluster to RECOVERING mode
    final var toRecovering = clusterActuator.updateMode("RECOVERING", false);
    Awaitility.await("cluster transitions to RECOVERING")
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(
            () ->
                ClusterActuatorAssert.assertThat(clusterActuator)
                    .hasCompletedChanges(toRecovering)
                    .doesNotHavePendingChanges());

    // then — backup ranges visible in recovery mode match those that were synced to the store
    final var stateInRecovery = backupActuator.state();
    final List<PartitionBackupRange> rangesInRecovery = stateInRecovery.getRanges();

    assertThat(rangesInRecovery)
        .describedAs("backup ranges must be non-empty in recovery mode")
        .isNotEmpty();
    assertThat(rangesInRecovery)
        .extracting(range -> range.getStart().getCheckpointId())
        .describedAs("range start checkpoint IDs must match those synced before mode transition")
        .containsExactlyInAnyOrderElementsOf(
            expectedRanges.stream().map(range -> range.getStart().getCheckpointId()).toList());
    assertThat(rangesInRecovery)
        .extracting(range -> range.getEnd().getCheckpointId())
        .describedAs("range end checkpoint IDs must match those synced before mode transition")
        .containsExactlyInAnyOrderElementsOf(
            expectedRanges.stream().map(range -> range.getEnd().getCheckpointId()).toList());

    // and — backup list visible in recovery mode contains the same backup IDs as before
    final List<BackupInfo> backupsInRecovery = backupActuator.list();
    assertThat(backupsInRecovery)
        .describedAs("backup list must be non-empty in recovery mode")
        .isNotEmpty();
    assertThat(backupsInRecovery)
        .extracting(BackupInfo::getBackupId)
        .describedAs(
            "backup IDs returned in recovery mode must include all IDs captured before transition")
        .containsAll(backupsBeforeRecovery.stream().map(BackupInfo::getBackupId).toList());

    // and — individual backup status lookup works in recovery mode
    final long knownBackupId = backupsBeforeRecovery.get(0).getBackupId();
    final var statusInRecovery = backupActuator.status(knownBackupId);
    assertThat(statusInRecovery.getBackupId())
        .describedAs("status lookup by backup ID must return the correct ID in recovery mode")
        .isEqualTo(knownBackupId);
  }

  private void generateLoad() {
    try (final var client = cluster.newClientBuilder().build()) {
      client
          .newPublishMessageCommand()
          .messageName(RandomStringUtils.insecure().next(8))
          .correlationKey(RandomStringUtils.insecure().next(8))
          .send()
          .join();
    }
  }
}
