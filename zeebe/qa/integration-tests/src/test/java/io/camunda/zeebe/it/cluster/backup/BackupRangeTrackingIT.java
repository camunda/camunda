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
