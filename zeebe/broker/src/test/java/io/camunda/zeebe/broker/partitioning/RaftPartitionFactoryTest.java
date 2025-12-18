/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.journal.file.SegmentAllocator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

public final class RaftPartitionFactoryTest {
  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Test
  void shouldSetElectionTimeout() {
    // given
    final Duration expected = Duration.ofSeconds(15);
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setElectionTimeout(expected);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().getElectionTimeout()).isEqualTo(expected);
  }

  @Test
  void shouldSetHeartbeatInterval() {
    // given
    final Duration expected = Duration.ofSeconds(10);
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().setHeartbeatInterval(expected);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().getHeartbeatInterval()).isEqualTo(expected);
  }

  @Test
  void shouldSetRaftRequestTimeout() {
    // given
    final Duration expected = Duration.ofSeconds(17);
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getRaft().setRequestTimeout(expected);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().getRequestTimeout()).isEqualTo(expected);
  }

  @Test
  void shouldSetRaftSnapshotRequestTimeout() {
    // given
    final Duration expected = Duration.ofSeconds(15);
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getRaft().setSnapshotRequestTimeout(expected);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().getSnapshotRequestTimeout()).isEqualTo(expected);
  }

  @Test
  void shouldSetRaftSnapshotChunkSize() {
    // given
    final var chunkSize = DataSize.ofMegabytes(2);
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getRaft().setSnapshotChunkSize(chunkSize);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().getSnapshotChunkSize())
        .isEqualTo(chunkSize.toBytes());
  }

  @Test
  void shouldSetRaftConfigurationChangeTimeout() {
    // given
    final Duration expected = Duration.ofSeconds(15);
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getRaft().setConfigurationChangeTimeout(expected);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().getConfigurationChangeTimeout()).isEqualTo(expected);
  }

  @Test
  void shouldSetRaftMaxQuorumResponseTimeout() {
    // given
    final Duration expected = Duration.ofSeconds(13);
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getRaft().setMaxQuorumResponseTimeout(expected);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().getMaxQuorumResponseTimeout()).isEqualTo(expected);
  }

  @Test
  void shouldSetRaftMinStepDownFailureCount() {
    // given
    final int expected = 8;
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getRaft().setMinStepDownFailureCount(expected);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().getMinStepDownFailureCount()).isEqualTo(expected);
  }

  @Test
  void shouldSetMaxAppendBatchSize() {
    // given
    final DataSize expected = DataSize.ofMegabytes(123);
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().setMaxAppendBatchSize(expected);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().getMaxAppendBatchSize())
        .isEqualTo(expected.toBytes());
  }

  @Test
  void shouldSetMaxAppendsPerFollower() {
    // given
    final int expected = 11;
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().setMaxAppendsPerFollower(expected);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().getMaxAppendsPerFollower()).isEqualTo(expected);
  }

  @Test
  void shouldEnablePriorityElection() {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().getRaft().setEnablePriorityElection(true);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().isPriorityElectionEnabled()).isTrue();
  }

  @Test
  void shouldDisablePriorityElection() {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getCluster().getRaft().setEnablePriorityElection(false);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().isPriorityElectionEnabled()).isFalse();
  }

  @Test
  void shouldSetPreferSnapshotReplicationThreshold() {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getRaft().setPreferSnapshotReplicationThreshold(1000);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    assertThat(partition.getPartitionConfig().getPreferSnapshotReplicationThreshold())
        .isEqualTo(1000);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSetSegmentFilesPreallocation(final boolean value) {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getRaft().setPreallocateSegmentFiles(value);

    // when
    final var partition = buildRaftPartition(brokerCfg);

    // then
    final var expected = value ? SegmentAllocator.defaultAllocator() : SegmentAllocator.noop();
    assertThat(partition.getPartitionConfig().getStorageConfig().getSegmentAllocator().getClass())
        .isEqualTo(expected.getClass());
  }

  private RaftPartition buildRaftPartition(final BrokerCfg brokerCfg) {
    return new RaftPartitionFactory(brokerCfg)
        .createRaftPartition(
            new PartitionMetadata(
                PartitionId.from("test", 1),
                Set.of(MemberId.from("1")),
                Map.of(),
                1,
                MemberId.from("1")),
            meterRegistry);
  }
}
