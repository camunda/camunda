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
import io.camunda.zeebe.backup.api.BackupRangeMarker.End;
import io.camunda.zeebe.backup.api.BackupRangeMarker.Start;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for the BackupRetention mechanism combined with the CheckpointScheduler. These
 * tests verify that backups follow a rolling window pattern and that old backups and range markers
 * are properly deleted.
 */
public interface BackupRetentionAcceptance extends ClockSupport {
  int PARTITION_COUNT = 3;
  int BROKER_COUNT = 3;
  int REPLICATION_FACTOR = 3;
  Duration BACKUP_INTERVAL = Duration.ofSeconds(5);
  Duration CLEANUP_INTERVAL = Duration.ofSeconds(5);
  Duration RETENTION_WINDOW = Duration.ofSeconds(15);

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
  default void shouldMaintainRollingWindowAndDeleteOldBackupsWithRangeMarkers() {

    pinClock(getTestCluster());
    final var actuator = BackupActuator.of(getTestCluster().availableGateway());
    final List<Long> backupIds = new ArrayList<>();

    // Wait for 3 completed backups
    final var firstBackup = awaitNewBackup(0);
    final var secondBackup = awaitNewBackup(firstBackup);
    final var thirdBackup = awaitNewBackup(secondBackup);
    backupIds.add(firstBackup);
    backupIds.add(secondBackup);

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
                  .as("Initial backup %s should have been deleted by retention", backupIds)
                  .doesNotContainAnyElementsOf(backupIds);
            });

    backupIds.forEach(this::assertManifestNotFoundFromStore);
    assertRangeMarkerHasBeenUpdated(thirdBackup);
  }

  default long awaitNewBackup(final long previousBackup) {
    progressClock(getTestCluster(), BACKUP_INTERVAL.toMillis());
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
                              .extracting(BackupStatus::statusCode)
                              .isEqualTo(BackupStatusCode.DOES_NOT_EXIST);
                        }));
  }

  default void assertRangeMarkerHasBeenUpdated(final long newStartBackupId) {
    IntStream.rangeClosed(1, PARTITION_COUNT)
        .forEach(
            partitionId -> {
              final var markers = getBackupStore().rangeMarkers(partitionId).join();
              AssertionsForClassTypes.assertThat(markers.size()).isEqualTo(2);
              final var startMarker = markers.stream().filter(Start.class::isInstance).findFirst();
              AssertionsForClassTypes.assertThat(startMarker)
                  .as("Start range marker for partition %d should be present", partitionId)
                  .isPresent();
              AssertionsForClassTypes.assertThat(startMarker.get().checkpointId())
                  .as("Start range marker for partition %d should be updated", partitionId)
                  .isEqualTo(newStartBackupId);

              final var endMarker = markers.stream().filter(End.class::isInstance).findFirst();
              AssertionsForClassTypes.assertThat(endMarker)
                  .as("End range marker for partition %d should be present", partitionId)
                  .isPresent();
              AssertionsForClassTypes.assertThat(endMarker.get().checkpointId())
                  .as("End range marker for partition %d should be updated", partitionId)
                  .isEqualTo(newStartBackupId);
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
