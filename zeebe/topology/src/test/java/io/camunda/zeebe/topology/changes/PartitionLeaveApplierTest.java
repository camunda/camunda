/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.topology.changes;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.ClusterConfigurationAssert;
import io.camunda.zeebe.topology.state.ClusterConfiguration;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import io.camunda.zeebe.topology.state.PartitionState.State;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PartitionLeaveApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId localMemberId = MemberId.from("1");
  final ClusterConfiguration initialClusterConfiguration =
      ClusterConfiguration.init()
          .addMember(localMemberId, MemberState.initializeAsActive(Map.of()));

  final PartitionLeaveApplier partitionLeaveApplier =
      new PartitionLeaveApplier(1, localMemberId, partitionChangeExecutor);

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
            localMemberId, m -> m.addPartition(1, PartitionState.active(1)));

    // when
    final var result = partitionLeaveApplier.init(topologyWithOneReplica);

    // then
    assertThat(result).isLeft();

    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("partition 1 has only one replica");
  }

  @Test
  void shouldUpdateStateToLeavingOnInit() {
    // given
    final ClusterConfiguration topologyWithPartition =
        initialClusterConfiguration
            .updateMember(localMemberId, m -> m.addPartition(1, PartitionState.active(1)))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()))
            .updateMember(MemberId.from("2"), m -> m.addPartition(1, PartitionState.active(1)));

    // when
    final var resultingTopology =
        partitionLeaveApplier.init(topologyWithPartition).get().apply(topologyWithPartition);

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(resultingTopology)
        .member(localMemberId)
        .hasPartitionWithState(1, new PartitionState(State.LEAVING, 1));
  }

  @Test
  void shouldExecuteLeaveOnApply() {
    // given
    final var topologyWithPartition =
        initialClusterConfiguration
            .updateMember(localMemberId, m -> m.addPartition(1, PartitionState.active(1)))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()))
            .updateMember(MemberId.from("2"), m -> m.addPartition(1, PartitionState.active(1)));

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
