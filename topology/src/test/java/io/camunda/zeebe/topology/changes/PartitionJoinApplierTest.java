/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.ClusterTopologyAssert;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import io.camunda.zeebe.topology.state.PartitionState.State;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.UnaryOperator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PartitionJoinApplierTest {
  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId localMemberId = MemberId.from("1");
  final ClusterTopology initialClusterTopology =
      ClusterTopology.init().addMember(localMemberId, MemberState.initializeAsActive(Map.of()));
  final PartitionJoinApplier partitionJoinApplier =
      new PartitionJoinApplier(1, 1, localMemberId, partitionChangeExecutor);

  @Test
  void shouldRejectJoinIfPartitionIsAlreadyJoined() {
    // given
    final ClusterTopology topologyWithPartitionJoined =
        initialClusterTopology.updateMember(
            localMemberId, m -> m.addPartition(1, PartitionState.active(1)));

    // when
    final Either<Exception, UnaryOperator<MemberState>> result =
        partitionJoinApplier.init(topologyWithPartitionJoined);

    // then
    assertThat(result).isLeft();

    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member already has the partition");
  }

  @Test
  void shouldRejectJoinIfPartitionIsAlreadyLeaving() {
    // given
    final ClusterTopology topologyWithPartitionLeaving =
        initialClusterTopology.updateMember(
            localMemberId, m -> m.addPartition(1, PartitionState.active(1).toLeaving()));

    // when - then
    final Either<Exception, UnaryOperator<MemberState>> result =
        partitionJoinApplier.init(topologyWithPartitionLeaving);
    assertThat(result).isLeft();

    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member already has the partition");
  }

  @Test
  void shouldRejectJoinIfMemberIsNotActive() {
    // given
    final ClusterTopology topologyWithMemberNotActive =
        initialClusterTopology.updateMember(localMemberId, MemberState::toLeaving);

    // when
    final Either<Exception, UnaryOperator<MemberState>> result =
        partitionJoinApplier.init(topologyWithMemberNotActive);

    // then
    assertThat(result).isLeft();

    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member is not active");
  }

  @Test
  void shouldRejectJoinIfMemberDoesNotExists() {
    // given
    final ClusterTopology topologyWithoutMember = ClusterTopology.init();
    // when
    final Either<Exception, UnaryOperator<MemberState>> result =
        partitionJoinApplier.init(topologyWithoutMember);

    // then
    assertThat(result).isLeft();

    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member is not active");
  }

  @Test
  void shouldRejectJoinIfPartitionDoesNotHaveActiveMembers() {
    // given
    final var topologyWithoutActiveMembers =
        ClusterTopology.init().addMember(localMemberId, MemberState.initializeAsActive(Map.of()));

    // when
    final var result = partitionJoinApplier.init(topologyWithoutActiveMembers);

    // then
    assertThat(result).isLeft();

    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("partition has no active members");
  }

  @Test
  void shouldInitializeStateToJoining() {
    // when
    final var updater = partitionJoinApplier.init(initialClusterTopology).get();
    final var resultingTopology = initialClusterTopology.updateMember(localMemberId, updater);

    // then
    ClusterTopologyAssert.assertThatClusterTopology(resultingTopology)
        .hasMemberWithPartitions(1, Set.of(1))
        .member(localMemberId)
        .hasPartitionWithState(1, new PartitionState(State.JOINING, 1));
  }

  @Test
  void shouldExecuteJoinCallBack() {
    // given
    final var updatedTopology =
        initialClusterTopology.updateMember(
            localMemberId, partitionJoinApplier.init(initialClusterTopology).get());
    when(partitionChangeExecutor.join(anyInt(), any()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var updater = partitionJoinApplier.apply().join();
    final var resultingTopology = updatedTopology.updateMember(localMemberId, updater);

    // then
    verify(partitionChangeExecutor, times(1)).join(anyInt(), any());
    ClusterTopologyAssert.assertThatClusterTopology(resultingTopology)
        .hasMemberWithPartitions(1, Set.of(1))
        .member(localMemberId)
        .hasPartitionWithState(1, new PartitionState(State.ACTIVE, 1));
  }

  @Test
  void shouldReturnExceptionWhenJoinFailed() {
    // given
    when(partitionChangeExecutor.join(anyInt(), any()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("Expected")));

    // when
    final var joinFuture = partitionJoinApplier.apply();

    // then
    Assertions.assertThat(joinFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(RuntimeException.class)
        .withMessageContaining("Expected");
  }
}
