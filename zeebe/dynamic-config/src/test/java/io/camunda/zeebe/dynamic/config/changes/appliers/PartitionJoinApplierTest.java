/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PartitionJoinApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId localMemberId = MemberId.from("1");
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  // Cluster-wide view with the local member ACTIVE — the precondition most tests exercise.
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
  void shouldRejectJoinIfLocalMemberIsNotPartOfCluster() {
    // given — the local member is absent from GlobalConfiguration entirely
    final var globalConfigurationWithoutLocalMember = globalConfigurationWith(Map.of());
    final var initialGroup =
        groupWithMembers(
            Map.of(
                MemberId.from("2"),
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result =
        new PartitionJoinApplier(localMemberId, 1, 1, partitionChangeExecutor)
            .init(globalConfigurationWithoutLocalMember, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member is not an active member of the cluster");
  }

  @Test
  void shouldRejectJoinIfLocalMemberIsNotActiveInCluster() {
    // given — the local member is part of the cluster, but not ACTIVE (e.g. still JOINING)
    final var globalConfigurationWithJoiningMember =
        globalConfigurationWith(
            Map.of(localMemberId, BrokerState.uninitialized().setState(BrokerState.State.JOINING)));
    final var initialGroup =
        groupWithMembers(
            Map.of(
                MemberId.from("2"),
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result =
        new PartitionJoinApplier(localMemberId, 1, 1, partitionChangeExecutor)
            .init(globalConfigurationWithJoiningMember, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member is not an active member of the cluster");
  }

  @Test
  void shouldRejectJoinIfPartitionIsAlreadyJoined() {
    // given — the local member is a member of the group and already hosts the partition, ACTIVE
    final var groupWithPartitionJoined =
        groupWithMembers(
            Map.of(
                localMemberId, brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result =
        new PartitionJoinApplier(localMemberId, 1, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, groupWithPartitionJoined);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member already has the partition");
  }

  @Test
  void shouldRejectJoinIfPartitionDoesNotHaveActiveMembersInGroup() {
    // given — the local member is in the group, but no one hosts the partition yet
    final var groupWithoutActiveMembers =
        groupWithMembers(Map.of(localMemberId, brokerWith(Map.of())));

    // when
    final var result =
        new PartitionJoinApplier(localMemberId, 1, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, groupWithoutActiveMembers);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("partition has no active members in the group");
  }

  @Test
  void shouldInitializeStateToJoiningWhenMemberIsNewToTheGroup() {
    // given — the local member is not yet a member of this group at all; member "2" hosts the
    // partition already
    final var initialGroup =
        groupWithMembers(
            Map.of(
                MemberId.from("2"),
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var updater =
        new PartitionJoinApplier(localMemberId, 1, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup)
            .get();
    final var resultingGroup = updater.apply(initialGroup);

    // then — the local member is added to the group with the partition in JOINING state
    Assertions.assertThat(resultingGroup.hasMember(localMemberId)).isTrue();
    final var localPartition = resultingGroup.getMember(localMemberId).getPartition(1);
    Assertions.assertThat(localPartition.state()).isEqualTo(State.JOINING);
    Assertions.assertThat(localPartition.priority()).isEqualTo(1);
  }

  @Test
  void shouldInitializeStateToJoiningWhenMemberAlreadyHasOtherPartitionsInTheGroup() {
    // given — the local member already replicates a different partition in this group
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId,
                brokerWith(Map.of(2, PartitionState.active(1, partitionConfig))),
                MemberId.from("2"),
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var updater =
        new PartitionJoinApplier(localMemberId, 1, 3, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup)
            .get();
    final var resultingGroup = updater.apply(initialGroup);

    // then — the new partition is added alongside the existing one
    final var localBroker = resultingGroup.getMember(localMemberId);
    Assertions.assertThat(localBroker.getPartition(2).state()).isEqualTo(State.ACTIVE);
    Assertions.assertThat(localBroker.getPartition(1).state()).isEqualTo(State.JOINING);
    Assertions.assertThat(localBroker.getPartition(1).priority()).isEqualTo(3);
  }

  @Test
  void shouldNotFailOnInitIfPartitionIsAlreadyJoiningLocally() {
    // given — the local member already has the partition in JOINING state (restart-safe retry)
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId,
                brokerWith(Map.of(1, PartitionState.joining(1, partitionConfig))),
                MemberId.from("2"),
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result =
        new PartitionJoinApplier(localMemberId, 1, 1, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isRight();
    Assertions.assertThat(
            result.get().apply(initialGroup).getMember(localMemberId).getPartition(1).state())
        .isEqualTo(State.JOINING);
  }

  @Test
  void shouldExecuteJoinCallback() {
    // given
    final var initialGroup =
        groupWithMembers(
            Map.of(
                MemberId.from("2"),
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));
    final var applier = new PartitionJoinApplier(localMemberId, 1, 1, partitionChangeExecutor);
    final var groupWithJoining =
        applier
            .init(globalConfigurationWithLocalMemberActive, initialGroup)
            .get()
            .apply(initialGroup);
    when(partitionChangeExecutor.join(anyInt(), any(), any()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var resultingGroup = applier.apply().join().apply(groupWithJoining);

    // then
    verify(partitionChangeExecutor, times(1)).join(anyInt(), any(), any());
    Assertions.assertThat(resultingGroup.getMember(localMemberId).getPartition(1).state())
        .isEqualTo(State.ACTIVE);
  }

  @Test
  void shouldReturnExceptionWhenJoinFailed() {
    // given
    when(partitionChangeExecutor.join(anyInt(), any(), any()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("Expected")));

    // when
    final var joinFuture =
        new PartitionJoinApplier(localMemberId, 1, 1, partitionChangeExecutor).apply();

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
    final var config = new DynamicPartitionConfig(ExportingConfig.init());
    final var initialGroup =
        groupWithMembers(
            Map.of(MemberId.from("2"), brokerWith(Map.of(1, PartitionState.active(1, config)))));

    // when
    final var updater =
        new PartitionJoinApplier(localMemberId, 1, 2, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup)
            .get();
    final var resultingGroup = updater.apply(initialGroup);

    // then
    final var localPartition = resultingGroup.getMember(localMemberId).getPartition(1);
    Assertions.assertThat(localPartition.priority()).isEqualTo(2);
    Assertions.assertThat(localPartition.state()).isEqualTo(State.JOINING);
    Assertions.assertThat(localPartition.config()).isEqualTo(config);
  }
}
