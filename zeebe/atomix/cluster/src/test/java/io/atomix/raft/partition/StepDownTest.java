/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.partition;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

final class StepDownTest {
  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Test
  void shouldStepDownWhenPriorityElectionEnabled(@TempDir final Path tempDir)
      throws IllegalAccessException {
    // given
    final var partitionId = new PartitionId("group", 1);
    final var metadata = new PartitionMetadata(partitionId, Set.of(), Map.of(), 1, null);

    final var raftPartitionConfig = new RaftPartitionConfig();
    raftPartitionConfig.setPriorityElectionEnabled(true);
    final var partition =
        new RaftPartition(metadata, raftPartitionConfig, tempDir.toFile(), meterRegistry);
    final var mockRaftPartitionServer = Mockito.mock(RaftPartitionServer.class);
    when(mockRaftPartitionServer.stepDown()).thenReturn(CompletableFuture.completedFuture(null));

    // To avoid having to start a server, just mock one.
    FieldUtils.writeField(partition, "server", mockRaftPartitionServer, true);

    // when
    partition.stepDown();

    // then
    verify(mockRaftPartitionServer).stepDown();
  }

  @Test
  void shouldNotStepDownWhenPriorityElectionDisabled(@TempDir final Path tempDir)
      throws IllegalAccessException {
    // given
    final var partitionId = new PartitionId("group", 1);
    final var raftPartitionConfig = new RaftPartitionConfig();
    raftPartitionConfig.setPriorityElectionEnabled(false);
    final var metadata = new PartitionMetadata(partitionId, Set.of(), Map.of(), 1, null);
    final var partition =
        new RaftPartition(metadata, raftPartitionConfig, tempDir.toFile(), meterRegistry);
    final var mockRaftPartitionServer = Mockito.mock(RaftPartitionServer.class);

    // To avoid having to start a server, just mock one.
    FieldUtils.writeField(partition, "server", mockRaftPartitionServer, true);

    // when
    partition.stepDown();

    // then
    verify(mockRaftPartitionServer, never()).stepDown();
  }

  @Test
  void shouldNotStepDownWhenServerIsNull(@TempDir final Path tempDir) {
    // given
    final var partitionId = new PartitionId("group", 1);
    final var raftPartitionConfig = new RaftPartitionConfig();
    raftPartitionConfig.setPriorityElectionEnabled(true);
    final var metadata = new PartitionMetadata(partitionId, Set.of(), Map.of(), 1, null);
    final var partition =
        new RaftPartition(metadata, raftPartitionConfig, tempDir.toFile(), meterRegistry);
    // server is null by default

    // when
    final var result = partition.stepDown();

    // then - should complete successfully without doing anything
    assertThat(result).isCompletedWithValue(null);
  }
}
