/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft.partition.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

public class RaftPartitionServerTest {

  private final RaftPartition raftPartition = mock(RaftPartition.class);

  @Test
  public void testInitServerRuntimeExceptionReturnsExceptionalFuture() {
    // given
    final MemberId localMemberId = new MemberId("1");
    when(raftPartition.members()).thenReturn(List.of(localMemberId));
    when(raftPartition.id())
        .thenReturn(PartitionId.from("group", Integer.parseInt(localMemberId.id())));
    final var raftPartitionConfig = mock(RaftPartitionConfig.class);

    final RaftPartitionServer raftPartitionServer =
        new RaftPartitionServer(
            raftPartition,
            new RaftPartitionConfig(),
            localMemberId,
            mock(ClusterMembershipService.class),
            mock(ClusterCommunicationService.class),
            mock(ReceivableSnapshotStore.class),
            mock(PartitionMetadata.class));

    // this is called internally by #initServer which we need to ensure does not prevent
    // a completableFuture to be returned on failure
    when(raftPartitionConfig.getStorageConfig()).thenThrow(RuntimeException.class);

    // when
    final CompletableFuture<RaftPartitionServer> raftServerStartFuture =
        raftPartitionServer.bootstrap();

    // then
    assertThat(raftServerStartFuture)
        .failsWithin(Duration.ZERO)
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(RuntimeException.class);
  }
}
