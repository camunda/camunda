/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.atomix.primitive.partition.PartitionManagementService;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
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

final class RaftPartitionJoinTest {
  @AutoClose MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private RaftPartition partitionWithRunningServer(
      final Path tempDir, final RaftPartitionServer server) throws IllegalAccessException {
    final var metadata =
        new PartitionMetadata(new PartitionId("system", 1), Set.of(), Map.of(), 1, null);
    final var partition =
        new RaftPartition(metadata, new RaftPartitionConfig(), tempDir.toFile(), meterRegistry);
    // To avoid having to start a server, just mock one.
    FieldUtils.writeField(partition, "server", server, true);
    return partition;
  }

  @Test
  void shouldRejectStartWithoutConfigurationWhileServerRuns(@TempDir final Path tempDir)
      throws IllegalAccessException {
    // given
    final var running = mock(RaftPartitionServer.class);
    final var partition = partitionWithRunningServer(tempDir, running);

    // when
    final var join =
        partition.startWithoutConfiguration(
            mock(PartitionManagementService.class), mock(ReceivableSnapshotStore.class), 7);

    // then - the running server is neither replaced nor started again
    assertThat(join)
        .failsWithin(java.time.Duration.ofSeconds(5))
        .withThrowableThat()
        .withRootCauseInstanceOf(IllegalStateException.class)
        .withMessageContaining("still running");
    assertThat(FieldUtils.readField(partition, "server", true)).isSameAs(running);
    verify(running, never()).startWithoutConfiguration(anyLong());
    verify(running, never()).join(any());
  }

  @Test
  void shouldRejectUntypedJoinWhileServerRuns(@TempDir final Path tempDir)
      throws IllegalAccessException {
    // given
    final var running = mock(RaftPartitionServer.class);
    final var partition = partitionWithRunningServer(tempDir, running);

    // when
    final var join =
        partition.join(
            Type.PASSIVE,
            mock(PartitionManagementService.class),
            mock(ReceivableSnapshotStore.class));

    // then
    assertThat(join).isCompletedExceptionally();
    verify(running, never()).join(any());
  }

  @Test
  void shouldNotRejectStartWithoutConfigurationAfterServerIsClosed(@TempDir final Path tempDir)
      throws IllegalAccessException {
    // given
    final var closed = mock(RaftPartitionServer.class);
    org.mockito.Mockito.when(closed.stop()).thenReturn(CompletableFuture.completedFuture(null));
    final var partition = partitionWithRunningServer(tempDir, closed);
    partition.close().join();

    // when - the join creates a new server; the mocked management service cannot start it
    final var managementService = mock(PartitionManagementService.class);
    catchThrowable(
        () ->
            partition
                .startWithoutConfiguration(
                    managementService, mock(ReceivableSnapshotStore.class), 7)
                .join());

    // then - the guard did not reject the join: the partition started to create a new server
    verify(managementService, atLeastOnce()).getMembershipService();
  }
}
