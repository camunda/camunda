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

final class PartitionPromoteApplierTest {

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

  private PartitionState learner() {
    return PartitionState.joining(1, partitionConfig).toLearner();
  }

  @Test
  void shouldRejectPromoteIfLocalMemberIsNotActiveInCluster() {
    // given
    final var globalConfigurationWithoutLocalMember = globalConfigurationWith(Map.of());
    final var initialGroup =
        groupWithMembers(Map.of(localMemberId, brokerWith(Map.of(1, learner()))));

    // when
    final var result =
        new PartitionPromoteApplier(localMemberId, 1, partitionChangeExecutor)
            .init(globalConfigurationWithoutLocalMember, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not an active member of the cluster");
  }

  @Test
  void shouldRejectPromoteIfLocalMemberDoesNotHavePartition() {
    // given
    final var initialGroup = groupWithMembers(Map.of(localMemberId, brokerWith(Map.of())));

    // when
    final var result =
        new PartitionPromoteApplier(localMemberId, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member does not have the partition");
  }

  @Test
  void shouldRejectPromoteIfPartitionIsNotALearner() {
    // given — the partition is still JOINING, so the join operation has not completed yet
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId, brokerWith(Map.of(1, PartitionState.joining(1, partitionConfig)))));

    // when
    final var result =
        new PartitionPromoteApplier(localMemberId, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the partition is in state JOINING");
  }

  @Test
  void shouldNotFailOnInitIfPartitionIsAlreadyActive() {
    // given — the node restarted after the promotion completed but before recording the operation
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId, brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result =
        new PartitionPromoteApplier(localMemberId, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isRight();
  }

  @Test
  void shouldExecutePromoteCallbackAndMarkPartitionActive() {
    // given
    final var initialGroup =
        groupWithMembers(Map.of(localMemberId, brokerWith(Map.of(1, learner()))));
    final var applier = new PartitionPromoteApplier(localMemberId, 1, partitionChangeExecutor);
    final var groupAfterInit =
        applier
            .init(globalConfigurationWithLocalMemberActive, initialGroup)
            .get()
            .apply(initialGroup);
    when(partitionChangeExecutor.promote(anyInt()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var resultingGroup = applier.apply().join().apply(groupAfterInit);

    // then
    verify(partitionChangeExecutor, times(1)).promote(1);
    Assertions.assertThat(resultingGroup.getMember(localMemberId).getPartition(1).state())
        .isEqualTo(State.ACTIVE);
  }

  @Test
  void shouldFailApplyWhilePromotionIsRejected() {
    // given — the member is not caught up yet, so the leader rejects the promotion; the reconciler
    // retries apply() with backoff until the catch-up gate accepts
    final var initialGroup =
        groupWithMembers(Map.of(localMemberId, brokerWith(Map.of(1, learner()))));
    final var applier = new PartitionPromoteApplier(localMemberId, 1, partitionChangeExecutor);
    applier.init(globalConfigurationWithLocalMemberActive, initialGroup);
    when(partitionChangeExecutor.promote(anyInt()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(
                new RuntimeException("not caught up yet")));

    // when
    final var result = applier.apply();

    // then
    Assertions.assertThat(result.isCompletedExceptionally()).isTrue();
  }
}
