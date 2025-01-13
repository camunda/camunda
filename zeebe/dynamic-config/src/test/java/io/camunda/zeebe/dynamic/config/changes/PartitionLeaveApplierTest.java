/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PartitionLeaveApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId localMemberId = MemberId.from("1");
  final PartitionLeaveApplier partitionLeaveApplier =
      new PartitionLeaveApplier(1, localMemberId, 1, partitionChangeExecutor);
  private final ClusterConfiguration initialClusterConfiguration =
      ClusterConfiguration.init()
          .addMember(localMemberId, MemberState.initializeAsActive(Map.of()));
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldRejectLeaveWhenPartitionDoesNotExist() {
    // when
    final var result = partitionLeaveApplier.init(initialClusterConfiguration);

    // then
    assertThat(result).isLeft();

    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not have the partition");
  }

  @Test
  void shouldRejectLeaveWhenPartitionHasOnlyOneReplica() {
    // given
    final ClusterConfiguration topologyWithOneReplica =
        initialClusterConfiguration.updateMember(
            localMemberId, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)));

    // when
    final var result = partitionLeaveApplier.init(topologyWithOneReplica);

    // then
    assertThat(result).isLeft();

    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("partition 1 has 1 replicas but minimum allowed replicas is 1");
  }

  @Test
  void shouldExecuteLeaveLastPartitionWhenPurged() {
    // given
    final ClusterConfiguration topologyWithOneReplica =
        initialClusterConfiguration
            .updateMember(
                localMemberId, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .startConfigurationChange(List.of(new DeleteHistoryOperation(localMemberId)));
    final var partitionLeaveApplierForPurge =
        new PartitionLeaveApplier(1, localMemberId, 0, partitionChangeExecutor);

    // when
    final var resultingTopology =
        partitionLeaveApplierForPurge
            .init(topologyWithOneReplica)
            .get()
            .apply(topologyWithOneReplica);

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(resultingTopology)
        .member(localMemberId)
        .hasPartitionWithState(1, State.LEAVING);
  }

  @Test
  void shouldUpdateStateToLeavingOnInit() {
    // given
    final ClusterConfiguration topologyWithPartition =
        initialClusterConfiguration
            .updateMember(
                localMemberId, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()))
            .updateMember(
                MemberId.from("2"),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)));

    // when
    final var resultingTopology =
        partitionLeaveApplier.init(topologyWithPartition).get().apply(topologyWithPartition);

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(resultingTopology)
        .member(localMemberId)
        .hasPartitionWithState(1, State.LEAVING);
  }

  @Test
  void shouldExecuteLeaveOnApply() {
    // given
    final var topologyWithPartition =
        initialClusterConfiguration
            .updateMember(
                localMemberId, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()))
            .updateMember(
                MemberId.from("2"),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)));

    final var topologyAfterInit =
        partitionLeaveApplier.init(topologyWithPartition).get().apply(topologyWithPartition);

    when(partitionChangeExecutor.leave(1)).thenReturn(CompletableActorFuture.completed(null));

    // when
    final var resultingTopology = partitionLeaveApplier.apply().join().apply(topologyAfterInit);

    // then
    verify(partitionChangeExecutor).leave(1);
    ClusterConfigurationAssert.assertThatClusterTopology(resultingTopology)
        .member(localMemberId)
        .doesNotContainPartition(1);
  }

  @Test
  void shouldReturnExceptionWhenLeaveFailed() {
    // given
    when(partitionChangeExecutor.leave(anyInt()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("Expected")));

    // when
    final var joinFuture = partitionLeaveApplier.apply();

    // then
    Assertions.assertThat(joinFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(RuntimeException.class)
        .withMessageContaining("Expected");
  }
}
