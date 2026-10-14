/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.systempartition;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionState;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionStateController;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import java.nio.file.Files;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Verifies that the system partition bootstraps as a Raft group spanning every broker, and that it
 * persists its state as a single JSON file per snapshot, without any RocksDB involvement.
 */
@ZeebeIntegration
final class SystemPartitionBootstrapIT {

  private static final Duration TIMEOUT = Duration.ofMinutes(1);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  // differs from both the default and the data partitions' segment size
  private static final DataSize SYSTEM_SEGMENT_SIZE = DataSize.ofMegabytes(5);

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(3)
          .withEmbeddedGateway(true)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
          .withBrokerConfig(
              broker ->
                  broker.withUnifiedConfig(
                      cfg -> {
                        final var systemPartition = cfg.getCluster().getSystemPartition();
                        systemPartition.setEnabled(true);
                        systemPartition.setLogSegmentSize(SYSTEM_SEGMENT_SIZE);
                      }))
          .build();

  @Test
  void shouldBootstrapSystemPartitionOnEveryBroker() {
    // when
    awaitSingleLeader();

    // then
    assertThat(cluster.brokers().values())
        .allSatisfy(
            broker ->
                assertThat(systemPartition(broker).raftPartition().members())
                    .containsExactlyInAnyOrderElementsOf(cluster.brokers().keySet()));
  }

  @Test
  void shouldUseSystemPartitionSegmentSize() {
    // when
    awaitSingleLeader();

    // then
    assertThat(cluster.brokers().values())
        .allSatisfy(
            broker ->
                assertThat(
                        systemPartition(broker)
                            .raftPartition()
                            .getPartitionConfig()
                            .getStorageConfig()
                            .getSegmentSize())
                    .describedAs("system partition segment size of %s", broker.nodeId())
                    .isEqualTo(SYSTEM_SEGMENT_SIZE.toBytes()));
  }

  @Test
  void shouldPersistStateAsJsonSnapshotOnEveryBroker() throws Exception {
    // when
    awaitSnapshotOnEveryBroker();

    // then
    for (final var broker : cluster.brokers().values()) {
      final var snapshot = systemPartition(broker).latestSnapshot().orElseThrow();
      try (final var files = Files.list(snapshot.getPath())) {
        assertThat(files.map(file -> file.getFileName().toString()))
            .describedAs("state files of the system partition snapshot of %s", broker.nodeId())
            .filteredOn(name -> !name.equals(FileBasedSnapshotStoreImpl.METADATA_FILE_NAME))
            .containsExactly(SystemPartitionStateController.STATE_FILE);
      }
      assertThat(readState(snapshot)).isEqualTo(SystemPartitionState.initial());
    }
  }

  @Test
  void shouldRestoreStateFromSnapshotAfterRestart() {
    // given
    awaitSnapshotOnEveryBroker();
    final var broker = cluster.brokers().get(MemberId.from("0"));
    final var snapshotBeforeRestart =
        systemPartition(broker).latestSnapshot().orElseThrow().getId();

    // when
    broker.stop();
    broker.start();
    awaitSingleLeader();

    // then
    Awaitility.await("restarted broker restores the system partition state")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(systemPartition(broker).state())
                    .contains(SystemPartitionState.initial()));
    assertThat(systemPartition(broker).latestSnapshot())
        .describedAs("the snapshot taken before the restart is reused")
        .map(PersistedSnapshot::getId)
        .contains(snapshotBeforeRestart);
  }

  private void awaitSingleLeader() {
    Awaitility.await("system partition elects a single leader")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(cluster.brokers().values())
                    .extracting(broker -> systemPartition(broker).raftPartition().getRole())
                    .containsOnlyOnce(Role.LEADER)
                    .containsOnly(Role.LEADER, Role.FOLLOWER));
  }

  private void awaitSnapshotOnEveryBroker() {
    Awaitility.await("every broker persists a system partition snapshot")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(cluster.brokers().values())
                    .allSatisfy(
                        broker ->
                            assertThat(systemPartition(broker).latestSnapshot()).isPresent()));
  }

  private static SystemPartitionState readState(final PersistedSnapshot snapshot) throws Exception {
    return MAPPER.readValue(
        snapshot.getPath().resolve(SystemPartitionStateController.STATE_FILE).toFile(),
        SystemPartitionState.class);
  }

  private static SystemPartition systemPartition(final TestStandaloneBroker broker) {
    final var systemPartition = broker.bean(Broker.class).getBrokerContext().getSystemPartition();
    assertThat(systemPartition).describedAs("system partition of %s", broker.nodeId()).isNotNull();
    return systemPartition;
  }
}
