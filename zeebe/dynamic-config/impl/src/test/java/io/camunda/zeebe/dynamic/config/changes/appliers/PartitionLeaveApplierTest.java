/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PartitionLeaveApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId localMemberId = MemberId.from("1");
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  private final GlobalConfiguration globalConfigurationWithLocalMemberActive =
      globalConfigurationWith(Map.of(localMemberId, BrokerState.initializeAsActive()));

  private static GlobalConfiguration globalConfigurationWith(
      final Map<MemberId, BrokerState> members) {
    return new GlobalConfiguration(
        GlobalConfiguration.INITIAL_VERSION,
        Optional.empty(),
        members,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private static PartitionGroupConfiguration groupWithMembers(
      final Map<MemberId, BrokerPartitionState> members) {
    return new PartitionGroupConfiguration(
        1, 0, members, Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static BrokerPartitionState brokerWith(final Map<Integer, PartitionState> partitions) {
    return new BrokerPartitionState(1, Instant.EPOCH, partitions, Mode.PROCESSING);
  }

  @Test
  void shouldRejectLeaveIfLocalMemberIsNotInCluster() {
    // given
    final var globalConfigurationWithoutLocalMember = globalConfigurationWith(Map.of());
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId, brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result =
        new PartitionLeaveApplier(localMemberId, 1, 0, partitionChangeExecutor)
            .init(globalConfigurationWithoutLocalMember, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member does not exist in the cluster");
  }

  @Test
  void shouldRejectLeaveIfLocalMemberDoesNotHavePartition() {
    // given
    final var initialGroup = groupWithMembers(Map.of(localMemberId, brokerWith(Map.of())));

    // when
    final var result =
        new PartitionLeaveApplier(localMemberId, 1, 0, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member does not have the partition");
  }

  @Test
  void shouldRejectLeaveIfReplicaCountAtMinimum() {
    // given — only one replica of the partition, minimum allowed is 1
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId, brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result =
        new PartitionLeaveApplier(localMemberId, 1, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("minimum allowed replicas");
  }

  @Test
  void shouldRejectLeaveIfOnlyOtherReplicaIsALearner() {
    // given — a learner does not participate in the quorum, so it must not be counted toward the
    // minimum: leaving the sole active replica here would strand the partition with only a
    // non-voting member, which raft then permanently rejects (no active member in a non-empty
    // configuration)
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig))),
                MemberId.from("2"),
                brokerWith(Map.of(1, PartitionState.joining(1, partitionConfig).toLearner()))));

    // when
    final var result =
        new PartitionLeaveApplier(localMemberId, 1, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("minimum allowed replicas");
  }

  @Test
  void shouldAllowLeaveWhenOtherReplicaIsRecovering() {
    // given — a recovering member has only paused its own stream processing; it is still a full
    // raft voter and counts toward the minimum
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig))),
                MemberId.from("2"),
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig).toRecovering()))));

    // when
    final var result =
        new PartitionLeaveApplier(localMemberId, 1, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isRight();
  }

  @Test
  void shouldAllowALearnerToLeaveEvenAtTheMinimum() {
    // given — the local member itself is the stuck learner being removed directly; removing a
    // non-voting member never changes how many voting replicas remain, so this must always be
    // allowed regardless of the minimum
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId,
                brokerWith(Map.of(1, PartitionState.joining(1, partitionConfig).toLearner())),
                MemberId.from("2"),
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result =
        new PartitionLeaveApplier(localMemberId, 1, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isRight();
  }

  @Test
  void shouldNotFailOnInitIfPartitionIsAlreadyLeaving() {
    // given — restart-safe retry
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig).toLeaving()))));

    // when
    final var result =
        new PartitionLeaveApplier(localMemberId, 1, 0, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isRight();
  }

  @Test
  void shouldMarkPartitionAsLeaving() {
    // given — two replicas, minimum allowed is 1, so leaving is allowed
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig))),
                MemberId.from("2"),
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var updater =
        new PartitionLeaveApplier(localMemberId, 1, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup)
            .get();
    final var resultingGroup = updater.apply(initialGroup);

    // then
    Assertions.assertThat(resultingGroup.getMember(localMemberId).getPartition(1).state())
        .isEqualTo(State.LEAVING);
  }

  @Test
  void shouldExecuteLeaveCallbackAndRemovePartition() {
    // given
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig))),
                MemberId.from("2"),
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));
    final var applier = new PartitionLeaveApplier(localMemberId, 1, 1, partitionChangeExecutor);
    final var groupWithLeaving =
        applier
            .init(globalConfigurationWithLocalMemberActive, initialGroup)
            .get()
            .apply(initialGroup);
    when(partitionChangeExecutor.leave(anyInt()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var resultingGroup = applier.apply().join().apply(groupWithLeaving);

    // then
    verify(partitionChangeExecutor, times(1)).leave(1);
    Assertions.assertThat(resultingGroup.getMember(localMemberId).hasPartition(1)).isFalse();
  }
}
