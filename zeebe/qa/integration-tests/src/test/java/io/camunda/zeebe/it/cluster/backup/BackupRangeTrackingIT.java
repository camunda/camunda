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
import io.camunda.management.backups.PartitionBackupRange;
import io.camunda.management.backups.PartitionBackupState;
import io.camunda.zeebe.backup.common.BackupMetadataCodec;
import io.camunda.zeebe.backup.common.BackupMetadataManifest;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupConfig;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
final class BackupRangeTrackingIT {
  private final Path tempDir;

  @TestZeebe(initMethod = "initTestCluster")
  private TestCluster cluster;

  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  BackupRangeTrackingIT(final @TempDir Path tempDir) {
    this.tempDir = tempDir;
  }

  @SuppressWarnings("unused")
  private void initTestCluster() {
    cluster =
        TestCluster.builder()
            .withBrokersCount(3)
            .withPartitionsCount(3)
            .withReplicationFactor(3)
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

          final var config = new Filesystem();
          config.setBasePath(tempDir.toAbsolutePath().toString());
          cfg.getData().getPrimaryStorage().getBackup().setFilesystem(config);

          cfg.getData().getPrimaryStorage().getBackup().setContinuous(true);
          cfg.getData()
              .getPrimaryStorage()
              .getBackup()
              .setSchedule(Duration.ofSeconds(5).toString());
        });
  }

  @Test
  void shouldTrackBackupRanges() {
    // given
    final var actuator = BackupActuator.of(cluster.availableGateway());

    // when
    executor.scheduleAtFixedRate(this::generateLoad, 0, 100, TimeUnit.MILLISECONDS);

    // then
    Awaitility.await("Until each partition has a backup range")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var states = actuator.state();
              final var ranges = states.getRanges();
              assertThat(ranges)
                  .describedAs("Each partition should have exactly one backup range")
                  .extracting(PartitionBackupRange::getPartitionId)
                  .containsExactlyInAnyOrder(1, 2, 3);
              assertThat(ranges)
                  .allSatisfy(
                      range -> {
                        assertThat(states.getBackupStates())
                            .satisfiesOnlyOnce(
                                (final PartitionBackupState state) -> {
                                  assertThat(state.getPartitionId())
                                      .isEqualTo(range.getPartitionId());
                                  assertThat(state.getCheckpointId())
                                      .isGreaterThanOrEqualTo(range.getStart().getCheckpointId());
                                });
                        assertThat(range.getEnd()).isNotEqualTo(range.getStart());
                      });
            });
  }

  @Test
  void shouldTrackBackupRangesDuringLeaderChanges() {
    // given
    final var actuator = BackupActuator.of(cluster.availableGateway());
    executor.scheduleAtFixedRate(this::generateLoad, 0, 100, TimeUnit.MILLISECONDS);

    // when
    final var initialLeader = cluster.leaderForPartition(1);
    initialLeader.stop();
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              try (final var client = cluster.newClientBuilder().build()) {
                final var topology = client.newTopologyRequest().send().join();
                TopologyAssert.assertThat(topology)
                    .describedAs("Initial leader is no longer part of the cluster")
                    .doesNotContainBroker(Integer.parseInt(initialLeader.nodeId().id()));
                TopologyAssert.assertThat(topology)
                    .describedAs("New leader for partition 1 should be elected")
                    .hasLeaderForEachPartition(3);
              }
            });

    final var lastBackupBeforeLeaderChange =
        actuator.state().getBackupStates().stream()
            .filter((final PartitionBackupState state) -> state.getPartitionId() == 1)
            .findFirst()
            .orElseThrow();

    // then
    Awaitility.await("New leader should continue to take backups and extend the existing range")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final io.camunda.management.backups.CheckpointState states = actuator.state();
              final var ranges = states.getRanges();
              final var lastBackupAfterLeaderChange =
                  actuator.state().getBackupStates().stream()
                      .filter((final PartitionBackupState state) -> state.getPartitionId() == 1)
                      .findFirst()
                      .orElseThrow();
              assertThat(lastBackupBeforeLeaderChange)
                  .describedAs("New leader for partition 1 should have taken a new backup")
                  .isNotEqualTo(lastBackupAfterLeaderChange);
              assertThat(ranges)
                  .describedAs("Each partition should still have exactly one backup range")
                  .extracting(PartitionBackupRange::getPartitionId)
                  .containsExactlyInAnyOrder(1, 2, 3);
              final var latestRange =
                  ranges.stream()
                      .filter(range -> range.getPartitionId() == 1)
                      .findFirst()
                      .orElseThrow();
              assertThat(latestRange.getEnd().getCheckpointId())
                  .describedAs("Range should end with the latest checkpoint")
                  .isEqualTo(lastBackupAfterLeaderChange.getCheckpointId());
            });
  }

  @Test
  void shouldAdvanceRangeStartOnBackupDeletion() {
    // given — wait for at least 3 backups per partition
    final var actuator = BackupActuator.of(cluster.availableGateway());
    executor.scheduleAtFixedRate(this::generateLoad, 0, 100, TimeUnit.MILLISECONDS);

    Awaitility.await("Until each partition has at least 3 backups in its range")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var ranges = actuator.state().getRanges();
              assertThat(ranges)
                  .extracting(PartitionBackupRange::getPartitionId)
                  .containsExactlyInAnyOrder(1, 2, 3);
              assertThat(ranges)
                  .allSatisfy(
                      range -> {
                        final var start = range.getStart().getCheckpointId();
                        final var end = range.getEnd().getCheckpointId();
                        // At least 3 distinct checkpoints in the range
                        assertThat(end - start).isGreaterThanOrEqualTo(2);
                      });
            });

    // Record the first checkpoint ID from the range (should be the same across all partitions)
    final var firstCheckpointId =
        actuator.state().getRanges().stream()
            .mapToLong(range -> range.getStart().getCheckpointId())
            .min()
            .orElseThrow();

    // when — delete the oldest backup
    actuator.delete(firstCheckpointId);

    // then — range start should advance past the deleted checkpoint
    Awaitility.await("Range start advances after deletion")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var ranges = actuator.state().getRanges();
              assertThat(ranges)
                  .extracting(PartitionBackupRange::getPartitionId)
                  .containsExactlyInAnyOrder(1, 2, 3);
              assertThat(ranges)
                  .allSatisfy(
                      range ->
                          assertThat(range.getStart().getCheckpointId())
                              .as(
                                  "Range start for partition %d should advance past deleted checkpoint %d",
                                  range.getPartitionId(), firstCheckpointId)
                              .isGreaterThan(firstCheckpointId));
            });

    // Verify JSON manifest reflects the deletion
    final var backupStore =
        FilesystemBackupStore.of(new FilesystemBackupConfig(tempDir.toAbsolutePath().toString()));

    IntStream.rangeClosed(1, 3)
        .forEach(
            partitionId -> {
              final var manifest =
                  BackupMetadataCodec.load(backupStore, partitionId).join().orElseThrow();

              assertThat(manifest.checkpoints())
                  .as(
                      "Manifest for partition %d should not contain deleted checkpoint %d",
                      partitionId, firstCheckpointId)
                  .extracting(BackupMetadataManifest.CheckpointEntry::checkpointId)
                  .doesNotContain(firstCheckpointId);

              assertThat(manifest.ranges())
                  .as("Manifest for partition %d should have at least one range", partitionId)
                  .isNotEmpty()
                  .allSatisfy(
                      range ->
                          assertThat(range.start())
                              .as("Manifest range start should be greater than deleted checkpoint")
                              .isGreaterThan(firstCheckpointId));
            });
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
