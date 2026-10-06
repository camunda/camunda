/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.management.backups.PartitionBackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that taking a backup through the backup API takes and reserves a fresh snapshot before
 * the checkpoint is written, uses exactly that snapshot for the backup, and releases it once the
 * backup is done.
 */
@ZeebeIntegration
final class BackupSnapshotReservationIT {

  private static final int PARTITION_ID = 1;

  @TempDir private static Path tempDir;
  private final Path backupBasePath = tempDir.resolve(UUID.randomUUID().toString());

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker().withBrokerConfig(this::configureBroker);

  private BackupActuator backupActuator;
  private PartitionsActuator partitionsActuator;

  @BeforeEach
  void setUp() {
    backupActuator = BackupActuator.of(broker);
    partitionsActuator = PartitionsActuator.of(broker);
  }

  @Test
  void shouldBackUpSnapshotTakenForTheCheckpoint() {
    // given - a snapshot that already exists before the backup is requested
    processSomeData();
    final var snapshotBeforeBackup = takeSnapshot(null);
    processSomeData();

    // when
    final var backup = takeAndAwaitBackup(1);

    // then - the backup uses a snapshot taken for this checkpoint, not the older one
    assertThat(backup.getSnapshotId()).isNotNull().isNotEqualTo(snapshotBeforeBackup);
    final var snapshotId = FileBasedSnapshotId.ofFileName(backup.getSnapshotId()).getOrThrow();
    assertThat(snapshotId.getProcessedPosition())
        .describedAs("snapshot must be strictly before the checkpoint")
        .isLessThan(backup.getCheckpointPosition());
  }

  @Test
  void shouldReleaseSnapshotOnceBackupIsCompleted() {
    // given
    processSomeData();
    final var backup = takeAndAwaitBackup(1);
    final var backedUpSnapshot = snapshotsDirectory().resolve(backup.getSnapshotId());
    assertThat(backedUpSnapshot).isDirectory();

    // when - a newer snapshot is persisted, which deletes older snapshots that are not reserved
    processSomeData();
    takeSnapshot(backup.getSnapshotId());

    // then
    await("snapshot of the completed backup is no longer reserved and gets deleted")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(backedUpSnapshot).doesNotExist());
  }

  private PartitionBackupInfo takeAndAwaitBackup(final long backupId) {
    backupActuator.take(backupId);
    await("backup is completed")
        .timeout(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(backupActuator.status(backupId).getState())
                    .isEqualTo(StateCode.COMPLETED));
    return backupActuator.status(backupId).getDetails().stream()
        .filter(info -> info.getPartitionId() == PARTITION_ID)
        .findFirst()
        .orElseThrow();
  }

  /** Takes a snapshot and waits until it replaced the given one, which may be null. */
  private String takeSnapshot(final String previousSnapshotId) {
    partitionsActuator.takeSnapshot();
    return await("a new snapshot is taken")
        .timeout(Duration.ofSeconds(30))
        .until(
            () -> partitionsActuator.query().get(PARTITION_ID).snapshotId(),
            snapshotId -> Objects.nonNull(snapshotId) && !snapshotId.equals(previousSnapshotId));
  }

  private Path snapshotsDirectory() {
    return broker
        .getWorkingDirectory()
        .resolve(DataCfg.DEFAULT_DIRECTORY)
        .resolve(PartitionManagerImpl.GROUP_NAME)
        .resolve("partitions")
        .resolve(String.valueOf(PARTITION_ID))
        .resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY);
  }

  private void processSomeData() {
    try (final var client = broker.newClientBuilder().build()) {
      for (int i = 0; i < 5; i++) {
        client
            .newPublishMessageCommand()
            .messageName("test-message")
            .correlationKey("key-" + i)
            .send()
            .join();
      }
    }
  }

  private void configureBroker(final BrokerCfg cfg) {
    final var filesystem = new FilesystemBackupStoreConfig();
    filesystem.setBasePath(backupBasePath.toAbsolutePath().toString());
    cfg.getData().getBackup().setStore(BackupStoreType.FILESYSTEM);
    cfg.getData().getBackup().setFilesystem(filesystem);
  }
}
