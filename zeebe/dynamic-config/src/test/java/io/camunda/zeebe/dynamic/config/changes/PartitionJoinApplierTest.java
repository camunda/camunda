/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert;
import io.camunda.zeebe.dynamic.config.PartitionStateAssert;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PartitionJoinApplierTest {
  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId localMemberId = MemberId.from("1");

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldRejectJoinIfPartitionIsAlreadyJoined() {
    // given
    final ClusterConfiguration topologyWithPartitionJoined =
        ClusterConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
            .updateMember(
                localMemberId, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)));

    // when
    final var result =
        new PartitionJoinApplier(1, 1, localMemberId, partitionChangeExecutor)
            .init(topologyWithPartitionJoined);

    // then
    assertThat(result).isLeft();

    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member already has the partition");
  }

  @Test
  void shouldRejectJoinIfPartitionIsAlreadyLeaving() {
    // given
    final ClusterConfiguration topologyWithPartitionLeaving =
        ClusterConfiguration.init()
            .addMember(
                localMemberId,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig).toLeaving())))
            .addMember(
                MemberId.from("2"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));

    // when - then
    final var result =
        new PartitionJoinApplier(1, 1, localMemberId, partitionChangeExecutor)
            .init(topologyWithPartitionLeaving);
    assertThat(result).isLeft();

    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member already has the partition");
  }

  @Test
  void shouldRejectJoinIfMemberIsNotActive() {
    // given
    final ClusterConfiguration topologyWithMemberNotActive =
        ClusterConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
            .updateMember(localMemberId, MemberState::toLeaving);

    // when
    final var result =
        new PartitionJoinApplier(1, 1, localMemberId, partitionChangeExecutor)
            .init(topologyWithMemberNotActive);

    // then
    assertThat(result).isLeft();

    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member is not active");
  }

  @Test
  void shouldRejectJoinIfMemberDoesNotExists() {
    // given
    final ClusterConfiguration topologyWithoutMember = ClusterConfiguration.init();
    // when
    final var result =
        new PartitionJoinApplier(1, 1, localMemberId, partitionChangeExecutor)
            .init(topologyWithoutMember);

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
        ClusterConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()));

    // when
    final var result =
        new PartitionJoinApplier(1, 1, localMemberId, partitionChangeExecutor)
            .init(topologyWithoutActiveMembers);

    // then
    assertThat(result).isLeft();

    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("partition has no active members");
  }

  @Test
  void shouldInitializeStateToJoining() {
    // when
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
            .addMember(
                new MemberId("2"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));
    final var updater =
        new PartitionJoinApplier(1, 1, localMemberId, partitionChangeExecutor)
            .init(initialTopology)
            .get();
    final var resultingTopology = updater.apply(initialTopology);

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(resultingTopology)
        .hasMemberWithPartitions(1, Set.of(1))
        .member(localMemberId)
        .hasPartitionWithState(1, State.JOINING)
        .hasPartitionWithPriority(1, 1);
  }

  @Test
  void shouldExecuteJoinCallBack() {
    // given
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
            .addMember(
                new MemberId("2"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));
    final var partitionJoinApplier =
        new PartitionJoinApplier(1, 1, localMemberId, partitionChangeExecutor);
    final var updatedTopology =
        partitionJoinApplier.init(initialTopology).get().apply(initialTopology);
    when(partitionChangeExecutor.join(anyInt(), any(), any()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var resultingTopology = partitionJoinApplier.apply().join().apply(updatedTopology);

    // then
    verify(partitionChangeExecutor, times(1)).join(anyInt(), any(), any());
    ClusterConfigurationAssert.assertThatClusterTopology(resultingTopology)
        .hasMemberWithPartitions(1, Set.of(1))
        .member(localMemberId)
        .hasPartitionWithState(1, State.ACTIVE)
        .hasPartitionWithPriority(1, 1);
  }

  @Test
  void shouldReturnExceptionWhenJoinFailed() {
    // given
    when(partitionChangeExecutor.join(anyInt(), any(), any()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("Expected")));

    // when
    final var joinFuture =
        new PartitionJoinApplier(1, 1, localMemberId, partitionChangeExecutor).apply();

    // then
    Assertions.assertThat(joinFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(RuntimeException.class)
        .withMessageContaining("Expected");
  }

  @Test
  void shouldInitializeConfigFromOtherMembers() {
    // given
    final var config =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of(
                    "expA",
                    new ExporterState(1, ExporterState.State.ENABLED, Optional.of("expB")))));
    final var initialTopology =
        ClusterConfiguration.init()
            .addMember(localMemberId, MemberState.initializeAsActive(Map.of()))
            .addMember(
                new MemberId("2"),
                MemberState.initializeAsActive(Map.of(1, PartitionState.active(1, config))));

    final var partitionJoinApplier =
        new PartitionJoinApplier(1, 2, localMemberId, partitionChangeExecutor);

    // when
    final var updater = partitionJoinApplier.init(initialTopology).get();
    final var resultingTopology = updater.apply(initialTopology);

    // then
    ClusterConfigurationAssert.assertThatClusterTopology(resultingTopology)
        .hasMemberWithPartitions(1, Set.of(1))
        .member(localMemberId)
        .hasPartitionSatisfying(
            1,
            p -> {
              PartitionStateAssert.assertThat(p)
                  .hasPriority(2)
                  .hasState(State.JOINING)
                  .hasConfig(config);
            });
  }
}
