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
import io.camunda.management.backups.PartitionBackupState;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for the BackupRetention mechanism combined with the CheckpointScheduler. These
 * tests verify that backups follow a rolling window pattern and that old backups are properly
 * deleted.
 */
public interface BackupRetentionAcceptance extends ClockSupport {
  int PARTITION_COUNT = 3;
  int BROKER_COUNT = 3;
  int REPLICATION_FACTOR = 3;
  // Backup tolerance to account for clock skew when taking a backup after advancing the clock
  int BACKUP_TOLERANCE_MILLIS = 200;
  Duration BACKUP_INTERVAL = Duration.ofSeconds(5);
  Duration CLEANUP_INTERVAL = Duration.ofSeconds(3);
  // Retention window is slightly longer than backup interval to account for clock drift
  Duration RETENTION_WINDOW = BACKUP_INTERVAL.plus(Duration.ofSeconds(3));

  TestCluster getTestCluster();

  BackupStore getBackupStore();

  default void containerSetup() {}

  @BeforeEach
  default void setup() {
    applyInitialConfig();
    containerSetup();
    getTestCluster().start().awaitCompleteTopology();
  }

  @Test
  default void shouldMaintainRollingWindowAndDeleteOldBackups() {

    pinClock(getTestCluster());
    final var actuator = BackupActuator.of(getTestCluster().availableGateway());

    // Wait for 4 completed backups
    final var firstBackup = awaitNewBackup(0);
    final var secondBackup = awaitNewBackup(firstBackup);
    final var thirdBackup = awaitNewBackup(secondBackup);
    awaitNewBackup(thirdBackup);

    restartClusterWithRetention();

    Awaitility.await("Retention deletes old backups")
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final var currentBackups = actuator.list();
              final var currentBackupIds =
                  currentBackups.stream().map(BackupInfo::getBackupId).toList();

              // Initial backups should have been deleted
              assertThat(currentBackupIds)
                  .as(
                      "Initial backup %s should have been deleted by retention",
                      List.of(firstBackup, secondBackup))
                  .doesNotContain(firstBackup, secondBackup)
                  .contains(thirdBackup);
            });

    assertManifestPresentInStore(thirdBackup);
    assertManifestNotFoundFromStore(firstBackup);
    assertManifestNotFoundFromStore(secondBackup);
  }

  default long awaitNewBackup(final long previousBackup) {
    progressClock(getTestCluster(), BACKUP_INTERVAL.toMillis() + BACKUP_TOLERANCE_MILLIS);
    final var actuator = BackupActuator.of(getTestCluster().availableGateway());
    Awaitility.await("Backup is created")
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () -> {
              final var state = actuator.state();
              if (state.getBackupStates().isEmpty()) {
                return false;
              }
              return state.getBackupStates().stream()
                  .filter(f -> Objects.nonNull(f.getCheckpointId()))
                  .allMatch(f -> f.getCheckpointId() > previousBackup);
            });
    return actuator.state().getBackupStates().stream()
        .filter(f -> Objects.nonNull(f.getCheckpointId()))
        .filter(f -> f.getCheckpointId() > previousBackup)
        .findFirst()
        .map(PartitionBackupState::getCheckpointId)
        .orElseThrow();
  }

  default void assertManifestNotFoundFromStore(final Long backupId) {
    IntStream.rangeClosed(1, PARTITION_COUNT)
        .forEach(
            partitionId ->
                IntStream.rangeClosed(0, BROKER_COUNT - 1)
                    .forEach(
                        brokerId -> {
                          final var identifier =
                              new BackupIdentifierImpl(brokerId, partitionId, backupId);
                          assertThat(getBackupStore().getStatus(identifier).join())
                              .withFailMessage(
                                  String.format(
                                      "Expected backup manifest for broker %d partition %d backupId %d not to be present in store",
                                      brokerId, partitionId, backupId))
                              .extracting(BackupStatus::statusCode)
                              .isEqualTo(BackupStatusCode.DOES_NOT_EXIST);
                        }));
  }

  default void assertManifestPresentInStore(final Long backupId) {
    IntStream.rangeClosed(1, PARTITION_COUNT)
        .forEach(
            partitionId -> {
              try (final var node = getTestCluster().leaderForPartition(partitionId)) {
                final var brokerId = Integer.parseInt(node.nodeId().id());
                final var identifier = new BackupIdentifierImpl(brokerId, partitionId, backupId);
                assertThat(getBackupStore().getStatus(identifier).join())
                    .withFailMessage(
                        String.format(
                            "Expected backup manifest for broker %d partition %d backupId %d to be present in store",
                            brokerId, partitionId, backupId))
                    .extracting(BackupStatus::statusCode)
                    .isEqualTo(BackupStatusCode.COMPLETED);
              }
            });
  }

  Consumer<Camunda> backupConfig();

  default void applyInitialConfig() {
    getTestCluster()
        .brokers()
        .values()
        .forEach(
            broker -> {
              broker.withUnifiedConfig(backupConfig());
              broker.withDataConfig(
                  data -> {
                    data.getPrimaryStorage().getBackup().setContinuous(true);
                    data.getPrimaryStorage()
                        .getBackup()
                        .setSchedule("PT" + BACKUP_INTERVAL.getSeconds() + "S");
                  });
            });
  }

  default void restartClusterWithRetention() {
    getTestCluster()
        .shutdown()
        .brokers()
        .values()
        .forEach(
            broker ->
                broker.withDataConfig(
                    data -> {
                      // Set schedule to something that will not trigger another backup
                      data.getPrimaryStorage().getBackup().setSchedule("PT5H");
                      data.getPrimaryStorage()
                          .getBackup()
                          .getRetention()
                          .setCleanupSchedule("PT" + CLEANUP_INTERVAL.getSeconds() + "S");
                      data.getPrimaryStorage()
                          .getBackup()
                          .getRetention()
                          .setWindow(RETENTION_WINDOW);
                    }));

    getTestCluster().start().awaitCompleteTopology();
  }
}
