/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.partition;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

final class StepDownIfNotPrimaryTest {
  @Test
  void shouldStepDownIfNotPrimary(@TempDir Path tempDir) throws IllegalAccessException {
    // given
    final var primaryMemberId = new MemberId("2");
    final var partitionId = new PartitionId("group", 1);
    final var raftPartitionConfig = new RaftPartitionConfig();
    final var partition =
        new RaftPartition(
            partitionId,
            new RaftPartitionGroupConfig().setPartitionConfig(raftPartitionConfig),
            tempDir.toFile());
    final var mockRaftPartitionServer = Mockito.mock(RaftPartitionServer.class);
    Mockito.when(mockRaftPartitionServer.getMemberId()).thenReturn(new MemberId("1"));

    // To avoid having to start a server, just mock one.
    FieldUtils.writeField(partition, "server", mockRaftPartitionServer, true);

    // when -- enabling priority election and marking a different member as primary
    raftPartitionConfig.setPriorityElectionEnabled(true);
    partition.setMetadata(
        new PartitionMetadata(partitionId, Set.of(), Map.of(), 1, primaryMemberId));

    // then -- current member should step down
    Assertions.assertThat(partition.shouldStepDown()).isTrue();
  }

  @Test
  void shouldNotStepDownIfPrimary(@TempDir Path tempDir) throws IllegalAccessException {
    // given
    final var partitionId = new PartitionId("group", 1);
    final var raftPartitionConfig = new RaftPartitionConfig();
    final var partition =
        new RaftPartition(
            partitionId,
            new RaftPartitionGroupConfig().setPartitionConfig(raftPartitionConfig),
            tempDir.toFile());

    final var mockRaftPartitionServer = Mockito.mock(RaftPartitionServer.class);
    Mockito.when(mockRaftPartitionServer.getMemberId()).thenReturn(new MemberId("1"));

    // To avoid having to start a server, just mock one.
    FieldUtils.writeField(partition, "server", mockRaftPartitionServer, true);

    // when -- enabling priority election and marking the current member as primary
    final var primaryMemberId = new MemberId("1");
    raftPartitionConfig.setPriorityElectionEnabled(true);
    partition.setMetadata(
        new PartitionMetadata(partitionId, Set.of(), Map.of(), 1, primaryMemberId));

    // then -- current member should not step down
    Assertions.assertThat(partition.shouldStepDown()).isFalse();
  }

  @Test
  void shouldNotStepDownIfPriorityElectionDisabled(@TempDir Path tempDir)
      throws IllegalAccessException {
    // given
    final var partitionId = new PartitionId("group", 1);
    final var raftPartitionConfig = new RaftPartitionConfig();
    final var partition =
        new RaftPartition(
            partitionId,
            new RaftPartitionGroupConfig().setPartitionConfig(raftPartitionConfig),
            tempDir.toFile());
    final var mockRaftPartitionServer = Mockito.mock(RaftPartitionServer.class);
    Mockito.when(mockRaftPartitionServer.getMemberId()).thenReturn(new MemberId("1"));

    // To avoid having to start a server, just mock one.
    FieldUtils.writeField(partition, "server", mockRaftPartitionServer, true);

    // when -- disabling priority election
    final var primaryMemberId = new MemberId("2");
    raftPartitionConfig.setPriorityElectionEnabled(false);
    partition.setMetadata(
        new PartitionMetadata(partitionId, Set.of(), Map.of(), 1, primaryMemberId));

    // then -- current member should not step down
    Assertions.assertThat(partition.shouldStepDown()).isFalse();
  }

  @Test
  void shouldNotStepDownIfPartitionHasNoPrimary(@TempDir Path tempDir)
      throws IllegalAccessException {
    // given
    final var partitionId = new PartitionId("group", 1);
    final var raftPartitionConfig = new RaftPartitionConfig();
    final var partition =
        new RaftPartition(
            partitionId,
            new RaftPartitionGroupConfig().setPartitionConfig(raftPartitionConfig),
            tempDir.toFile());
    final var mockRaftPartitionServer = Mockito.mock(RaftPartitionServer.class);
    Mockito.when(mockRaftPartitionServer.getMemberId()).thenReturn(new MemberId("1"));

    // To avoid having to start a server, just mock one.
    FieldUtils.writeField(partition, "server", mockRaftPartitionServer, true);

    // when -- no primary known for this partition
    final MemberId primaryMemberId = null;
    raftPartitionConfig.setPriorityElectionEnabled(true);
    partition.setMetadata(
        new PartitionMetadata(partitionId, Set.of(), Map.of(), 1, primaryMemberId));

    // then -- current member should not step down
    Assertions.assertThat(partition.shouldStepDown()).isFalse();
  }
}
