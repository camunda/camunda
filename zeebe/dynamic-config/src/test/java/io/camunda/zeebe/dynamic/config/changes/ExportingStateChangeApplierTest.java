/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class ExportingStateChangeApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);

  private final MemberId localMemberId = MemberId.from("1");
  private final ExportingState targetState = ExportingState.PAUSED;

  private final ExportingStateChangeApplier applier =
      new ExportingStateChangeApplier(localMemberId, targetState, partitionChangeExecutor);

  @Test
  void shouldFailInitIfMemberDoesNotExist() {
    // given
    final var clusterConfiguration = ClusterConfiguration.init();

    // when
    final var result = applier.initMemberState(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("member '1' does not exist");
  }

  @Test
  void shouldNotChangeStateInInit() {
    // given
    final var clusterConfiguration = clusterWithPartitions(1, 2);

    // when
    final var result = applier.initMemberState(clusterConfiguration);

    // then
    EitherAssert.assertThat(result).isRight();
    final MemberState memberState = clusterConfiguration.getMember(localMemberId);
    assertThat(result.get().apply(memberState)).isEqualTo(memberState);
  }

  @Test
  void shouldSetExporterStateOnAllLocalPartitions() {
    // given
    applier.initMemberState(clusterWithPartitions(1, 2));
    when(partitionChangeExecutor.setExportingState(1, targetState))
        .thenReturn(CompletableActorFuture.completed(null));
    when(partitionChangeExecutor.setExportingState(2, targetState))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    applier.applyOperation().join();

    // then
    verify(partitionChangeExecutor).setExportingState(1, targetState);
    verify(partitionChangeExecutor).setExportingState(2, targetState);
  }

  @Test
  void shouldUpdatePartitionConfigStateIfApplySucceeds() {
    // given
    final var clusterConfiguration = clusterWithPartitions(1, 2);
    applier.initMemberState(clusterConfiguration);
    when(partitionChangeExecutor.setExportingState(1, targetState))
        .thenReturn(CompletableActorFuture.completed(null));
    when(partitionChangeExecutor.setExportingState(2, targetState))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var result = applier.applyOperation();

    // then
    assertThat(result).succeedsWithin(Duration.ofMillis(100));
    final var updatedState = result.join().apply(clusterConfiguration.getMember(localMemberId));
    assertThat(updatedState.getPartition(1).config().exporting().state()).isEqualTo(targetState);
    assertThat(updatedState.getPartition(2).config().exporting().state()).isEqualTo(targetState);
  }

  @Test
  void shouldFailFutureIfAnyPartitionFails() {
    // given
    applier.initMemberState(clusterWithPartitions(1, 2));
    when(partitionChangeExecutor.setExportingState(1, targetState))
        .thenReturn(CompletableActorFuture.completed(null));
    when(partitionChangeExecutor.setExportingState(2, targetState))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("force fail")));

    // when
    final var result = applier.applyOperation();

    // then
    assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("force fail");
  }

  private ClusterConfiguration clusterWithPartitions(final int... partitionIds) {
    final var partitions = new java.util.HashMap<Integer, PartitionState>();
    for (final int partitionId : partitionIds) {
      partitions.put(partitionId, PartitionState.active(1, DynamicPartitionConfig.init()));
    }
    return ClusterConfiguration.init()
        .addMember(localMemberId, MemberState.initializeAsActive(Map.copyOf(partitions)));
  }
}
