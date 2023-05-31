/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.atomix.raft.partition.RaftPartitionGroupConfig;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStoreFactory;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

final class RaftPartitionGroupFactoryTest {
  private static final ReceivableSnapshotStoreFactory SNAPSHOT_STORE_FACTORY =
      (directory, partitionId) -> mock(ReceivableSnapshotStore.class);

  private final RaftPartitionGroupFactory factory = new RaftPartitionGroupFactory();
  private final BrokerCfg brokerCfg = new BrokerCfg();

  @Test
  void shouldSetElectionTimeout() {
    // given
    final Duration expected = Duration.ofSeconds(15);
    brokerCfg.getCluster().setElectionTimeout(expected);

    // when
    final var config = buildRaftPartitionGroup();

    // then
    assertThat(config.getPartitionConfig().getElectionTimeout()).isEqualTo(expected);
  }

  @Test
  void shouldSetHeartbeatInterval() {
    // given
    final Duration expected = Duration.ofSeconds(10);
    brokerCfg.getCluster().setHeartbeatInterval(expected);

    // when
    final var config = buildRaftPartitionGroup();

    // then
    assertThat(config.getPartitionConfig().getHeartbeatInterval()).isEqualTo(expected);
  }

  @Test
  void shouldSetRaftRequestTimeout() {
    // given
    final Duration expected = Duration.ofSeconds(17);
    brokerCfg.getExperimental().getRaft().setRequestTimeout(expected);

    // when
    final var config = buildRaftPartitionGroup();

    // then
    assertThat(config.getPartitionConfig().getRequestTimeout()).isEqualTo(expected);
  }

  @Test
  void shouldSetRaftSnapshotRequestTimeout() {
    // given
    final Duration expected = Duration.ofSeconds(15);
    brokerCfg.getExperimental().getRaft().setSnapshotRequestTimeout(expected);

    // when
    final var config = buildRaftPartitionGroup();

    // then
    assertThat(config.getPartitionConfig().getSnapshotRequestTimeout()).isEqualTo(expected);
  }

  @Test
  void shouldSetRaftMaxQuorumResponseTimeout() {
    // given
    final Duration expected = Duration.ofSeconds(13);
    brokerCfg.getExperimental().getRaft().setMaxQuorumResponseTimeout(expected);

    // when
    final var config = buildRaftPartitionGroup();

    // then
    assertThat(config.getPartitionConfig().getMaxQuorumResponseTimeout()).isEqualTo(expected);
  }

  @Test
  void shouldSetRaftMinStepDownFailureCount() {
    // given
    final int expected = 8;
    brokerCfg.getExperimental().getRaft().setMinStepDownFailureCount(expected);

    // when
    final var config = buildRaftPartitionGroup();

    // then
    assertThat(config.getPartitionConfig().getMinStepDownFailureCount()).isEqualTo(expected);
  }

  @Test
  void shouldSetMaxAppendBatchSize() {
    // given
    final DataSize expected = DataSize.ofMegabytes(123);
    brokerCfg.getExperimental().setMaxAppendBatchSize(expected);

    // when
    final var config = buildRaftPartitionGroup();

    // then
    assertThat(config.getPartitionConfig().getMaxAppendBatchSize()).isEqualTo(expected.toBytes());
  }

  @Test
  void shouldSetMaxAppendsPerFollower() {
    // given
    final int expected = 11;
    brokerCfg.getExperimental().setMaxAppendsPerFollower(expected);

    // when
    final var config = buildRaftPartitionGroup();

    // then
    assertThat(config.getPartitionConfig().getMaxAppendsPerFollower()).isEqualTo(expected);
  }

  @Test
  void shouldEnablePriorityElection() {
    // given
    brokerCfg.getCluster().getRaft().setEnablePriorityElection(true);

    // when
    final var config = buildRaftPartitionGroup();

    // then
    assertThat(config.getPartitionConfig().isPriorityElectionEnabled()).isTrue();
  }

  @Test
  void shouldDisablePriorityElection() {
    // given
    brokerCfg.getCluster().getRaft().setEnablePriorityElection(false);

    // when
    final var config = buildRaftPartitionGroup();

    // then
    assertThat(config.getPartitionConfig().isPriorityElectionEnabled()).isFalse();
  }

  @Test
  void shouldSetPreferSnapshotReplicationThreshold() {
    // given
    brokerCfg.getExperimental().getRaft().setPreferSnapshotReplicationThreshold(1000);

    // when
    final var config = buildRaftPartitionGroup();

    // then
    assertThat(config.getPartitionConfig().getPreferSnapshotReplicationThreshold()).isEqualTo(1000);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSetSegmentFilesPreallocation(final boolean value) {
    // given
    brokerCfg.getExperimental().getRaft().setPreallocateSegmentFiles(value);

    // when
    final var config = buildRaftPartitionGroup();

    // then
    assertThat(config.getStorageConfig().isPreallocateSegmentFiles()).isEqualTo(value);
  }

  private RaftPartitionGroupConfig buildRaftPartitionGroup() {
    final var partitionGroup = factory.buildRaftPartitionGroup(brokerCfg, SNAPSHOT_STORE_FACTORY);
    return (RaftPartitionGroupConfig) partitionGroup.config();
  }
}
