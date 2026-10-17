/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class ExitRecoveryApplierTest {

  private final ModeChangeExecutor modeChangeExecutor = mock(ModeChangeExecutor.class);
  private final MemberId memberId = MemberId.from("1");

  private final GlobalConfiguration globalConfigurationWithLocalMemberActive =
      globalConfigurationWith(Map.of(memberId, BrokerState.initializeAsActive()));

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

  @Test
  void shouldRejectIfLocalMemberIsNotActiveInCluster() {
    // given
    final var group =
        groupWithMembers(
            Map.of(
                memberId, new BrokerPartitionState(1, Instant.EPOCH, Map.of(), Mode.RECOVERING)));

    // when
    final var result =
        new ExitRecoveryApplier(memberId, modeChangeExecutor)
            .init(globalConfigurationWith(Map.of()), group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not an active member of the cluster");
  }

  @Test
  void shouldRejectIfLocalMemberIsNotPartOfGroup() {
    // given
    final var group = groupWithMembers(Map.of());

    // when
    final var result =
        new ExitRecoveryApplier(memberId, modeChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not part of this partition group");
  }

  @Test
  void shouldExecuteExitRecoveryCallbackAndSetModeProcessing() {
    // given
    final var group =
        groupWithMembers(
            Map.of(
                memberId, new BrokerPartitionState(1, Instant.EPOCH, Map.of(), Mode.RECOVERING)));
    final var applier = new ExitRecoveryApplier(memberId, modeChangeExecutor);
    when(modeChangeExecutor.exitRecovery()).thenReturn(CompletableActorFuture.completed(null));

    // when
    final var initResult = applier.init(globalConfigurationWithLocalMemberActive, group);
    assertThat(initResult).isRight();
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    verify(modeChangeExecutor, times(1)).exitRecovery();
    Assertions.assertThat(resultingGroup.getMember(memberId).mode()).isEqualTo(Mode.PROCESSING);
  }

  @Test
  void shouldSucceedAsNoOpOnInitWhenLocalMemberIsAlreadyProcessing() {
    // given — member is already in the target mode
    final var group =
        groupWithMembers(
            Map.of(
                memberId, new BrokerPartitionState(1, Instant.EPOCH, Map.of(), Mode.PROCESSING)));

    // when
    final var result =
        new ExitRecoveryApplier(memberId, modeChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, group);

    // then — this can happen if the node restarted while applying the operation; it must not be
    // treated as an error so the configuration change can make progress
    assertThat(result).isRight();
    final var resultingGroup = result.get().apply(group);
    Assertions.assertThat(resultingGroup.getMember(memberId).mode()).isEqualTo(Mode.PROCESSING);
  }

  @Test
  void shouldLeaveGroupUnchangedOnInitWhenMemberIsRecovering() {
    // given
    final var group =
        groupWithMembers(
            Map.of(
                memberId, new BrokerPartitionState(1, Instant.EPOCH, Map.of(), Mode.RECOVERING)));

    // when
    final var result =
        new ExitRecoveryApplier(memberId, modeChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, group);

    // then — init does not transition the mode; the flip to PROCESSING happens on apply
    assertThat(result).isRight();
    final var resultingGroup = result.get().apply(group);
    Assertions.assertThat(resultingGroup.getMember(memberId).mode()).isEqualTo(Mode.RECOVERING);
  }

  @Test
  void shouldDropThePartitionsAboveTheStableRoutingOnInit() {
    // given - a restore from a backup with 2 partitions routes the group over 2, while the member
    // still holds the 3rd partition of an earlier scale up
    final var group = groupWithRouting(new AllPartitions(2), Mode.RECOVERING, 1, 2, 3);

    // when
    final var result =
        new ExitRecoveryApplier(memberId, modeChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, group);

    // then
    assertThat(result).isRight();
    final var resultingGroup = result.get().apply(group);
    Assertions.assertThat(resultingGroup.getMember(memberId).partitions().keySet())
        .containsExactly(1, 2);
    Assertions.assertThat(resultingGroup.getMember(memberId).mode()).isEqualTo(Mode.RECOVERING);
  }

  @Test
  void shouldKeepEveryPartitionWhileTheRoutingIsUnstable() {
    // given - a scale up is adding partition 3, which is bootstrapped but not routed to yet
    final var group =
        groupWithRouting(new ActivePartitions(2, Set.of(), Set.of(3)), Mode.PROCESSING, 1, 2, 3);

    // when
    final var result =
        new ExitRecoveryApplier(memberId, modeChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, group);

    // then
    assertThat(result).isRight();
    Assertions.assertThat(result.get().apply(group).getMember(memberId).partitions().keySet())
        .containsExactly(1, 2, 3);
  }

  @Test
  void shouldKeepEveryPartitionTheGroupRoutesOver() {
    // given
    final var group = groupWithRouting(new AllPartitions(3), Mode.RECOVERING, 1, 2, 3);

    // when
    final var result =
        new ExitRecoveryApplier(memberId, modeChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, group);

    // then
    assertThat(result).isRight();
    Assertions.assertThat(result.get().apply(group).getMember(memberId).partitions().keySet())
        .containsExactly(1, 2, 3);
  }

  @Test
  void shouldKeepEveryPartitionWithoutARoutingState() {
    // given
    final var group =
        new PartitionGroupConfiguration(
            1,
            0,
            Map.of(memberId, memberWithPartitions(Mode.RECOVERING, 1, 2, 3)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    // when
    final var result =
        new ExitRecoveryApplier(memberId, modeChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, group);

    // then
    assertThat(result).isRight();
    Assertions.assertThat(result.get().apply(group).getMember(memberId).partitions().keySet())
        .containsExactly(1, 2, 3);
  }

  private PartitionGroupConfiguration groupWithRouting(
      final RequestHandling requestHandling, final Mode mode, final int... partitionIds) {
    return new PartitionGroupConfiguration(
        1,
        0,
        Map.of(memberId, memberWithPartitions(mode, partitionIds)),
        Optional.of(new RoutingState(2, requestHandling, new HashMod(2))),
        Optional.empty(),
        Optional.empty());
  }

  private static BrokerPartitionState memberWithPartitions(
      final Mode mode, final int... partitionIds) {
    final var partitions = new TreeMap<Integer, PartitionState>();
    for (final var partitionId : partitionIds) {
      partitions.put(partitionId, PartitionState.active(1, DynamicPartitionConfig.init()));
    }
    return new BrokerPartitionState(1, Instant.EPOCH, partitions, mode);
  }

  @Test
  void shouldFailApplyWhenExecutorFails() {
    // given
    final var applier = new ExitRecoveryApplier(memberId, modeChangeExecutor);
    when(modeChangeExecutor.exitRecovery())
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("Force failure")));

    // when
    final var result = applier.apply();

    // then
    Assertions.assertThat(result)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("Force failure");
  }
}
