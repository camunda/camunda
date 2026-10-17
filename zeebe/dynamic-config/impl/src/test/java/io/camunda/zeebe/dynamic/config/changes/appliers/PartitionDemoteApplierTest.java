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

final class PartitionDemoteApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId localMemberId = MemberId.from("1");
  private final MemberId otherMemberId = MemberId.from("2");
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
  void shouldRejectDemoteIfLocalMemberIsNotInCluster() {
    // given
    final var globalConfigurationWithoutLocalMember = globalConfigurationWith(Map.of());
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId, brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result =
        new PartitionDemoteApplier(localMemberId, 1, partitionChangeExecutor)
            .init(globalConfigurationWithoutLocalMember, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member does not exist in the cluster");
  }

  @Test
  void shouldRejectDemoteIfLocalMemberDoesNotHavePartition() {
    // given
    final var initialGroup = groupWithMembers(Map.of(localMemberId, brokerWith(Map.of())));

    // when
    final var result =
        new PartitionDemoteApplier(localMemberId, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member does not have the partition");
  }

  @Test
  void shouldRejectDemoteIfNoOtherReplicaExists() {
    // given — demoting the only replica would leave a non-empty replication group without any
    // voting member, which can neither elect a leader nor commit
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId, brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result =
        new PartitionDemoteApplier(localMemberId, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no other member has the partition in active state");
  }

  @Test
  void shouldRejectDemoteIfOtherReplicasAreNotActive() {
    // given — the other replica is still a learner, so it cannot vote either
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig))),
                otherMemberId,
                brokerWith(Map.of(1, PartitionState.joining(1, partitionConfig).toLearner()))));

    // when
    final var result =
        new PartitionDemoteApplier(localMemberId, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no other member has the partition in active state");
  }

  @Test
  void shouldAllowDemoteWhenOtherReplicaIsRecovering() {
    // given — a recovering member has only paused its own stream processing; it is still a full
    // raft voter, so it satisfies "another active replica exists"
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig))),
                otherMemberId,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig).toRecovering()))));

    // when
    final var result =
        new PartitionDemoteApplier(localMemberId, 1, partitionChangeExecutor)
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
        new PartitionDemoteApplier(localMemberId, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isRight();
  }

  @Test
  void shouldMarkPartitionAsLeaving() {
    // given
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig))),
                otherMemberId,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var updater =
        new PartitionDemoteApplier(localMemberId, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup)
            .get();
    final var resultingGroup = updater.apply(initialGroup);

    // then
    Assertions.assertThat(resultingGroup.getMember(localMemberId).getPartition(1).state())
        .isEqualTo(State.LEAVING);
  }

  @Test
  void shouldExecuteDemoteCallbackAndKeepPartitionLeaving() {
    // given — the demotion does not remove the partition; the subsequent leave operation does
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig))),
                otherMemberId,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));
    final var applier = new PartitionDemoteApplier(localMemberId, 1, partitionChangeExecutor);
    final var groupWithLeaving =
        applier
            .init(globalConfigurationWithLocalMemberActive, initialGroup)
            .get()
            .apply(initialGroup);
    when(partitionChangeExecutor.demote(anyInt()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var resultingGroup = applier.apply().join().apply(groupWithLeaving);

    // then
    verify(partitionChangeExecutor, times(1)).demote(1);
    Assertions.assertThat(resultingGroup.getMember(localMemberId).getPartition(1).state())
        .isEqualTo(State.LEAVING);
  }
}
