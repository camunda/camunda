/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.BrokerState.State;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class MemberLeaveApplierTest {

  private final MemberId memberId = MemberId.from("1");
  private final NoopClusterMembershipChangeExecutor clusterMembershipChangeExecutor =
      new NoopClusterMembershipChangeExecutor();

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

  private static CurrentClusterConfiguration currentClusterConfigurationWith(
      final GlobalConfiguration globalConfiguration,
      final Map<String, PartitionGroupConfiguration> partitionGroups) {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        globalConfiguration,
        partitionGroups,
        PhasedChangeState.empty());
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
  void shouldRejectLeaveIfMemberIsNotPartOfCluster() {
    // given
    final var initialConfig = globalConfigurationWith(Map.of());
    final var applier = new MemberLeaveApplier(memberId, clusterMembershipChangeExecutor);

    // when
    final var result = applier.init(currentClusterConfigurationWith(initialConfig, Map.of()));

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not part of the cluster");
  }

  @Test
  void shouldRejectLeaveIfMemberIsAlreadyLeft() {
    // given
    final var initialConfig =
        globalConfigurationWith(
            Map.of(memberId, BrokerState.initializeAsActive().setState(State.LEFT)));
    final var applier = new MemberLeaveApplier(memberId, clusterMembershipChangeExecutor);

    // when
    final var result = applier.init(currentClusterConfigurationWith(initialConfig, Map.of()));

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already in state LEFT");
  }

  @Test
  void shouldRejectLeaveIfMemberStillHasPartitions() {
    // given
    final var initialConfig =
        globalConfigurationWith(Map.of(memberId, BrokerState.initializeAsActive()));
    final var groupWithPartitions =
        groupWithMembers(
            Map.of(
                memberId,
                brokerWith(Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))));
    final var applier = new MemberLeaveApplier(memberId, clusterMembershipChangeExecutor);

    // when
    final var result =
        applier.init(
            currentClusterConfigurationWith(initialConfig, Map.of("default", groupWithPartitions)));

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("still has partitions assigned");
  }

  @Test
  void shouldRejectLeaveIfMemberHasPartitionsInAnyGroupNotJustTheFirst() {
    // given — the member has no partitions in "groupA", but does in "groupB"; the check must not
    // stop at the first group it happens to see
    final var initialConfig =
        globalConfigurationWith(Map.of(memberId, BrokerState.initializeAsActive()));
    final var groupWithoutPartitions = groupWithMembers(Map.of());
    final var groupWithPartitions =
        groupWithMembers(
            Map.of(
                memberId,
                brokerWith(Map.of(3, PartitionState.active(1, DynamicPartitionConfig.init())))));
    final var applier = new MemberLeaveApplier(memberId, clusterMembershipChangeExecutor);

    // when
    final var result =
        applier.init(
            currentClusterConfigurationWith(
                initialConfig,
                Map.of("groupA", groupWithoutPartitions, "groupB", groupWithPartitions)));

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("still has partitions assigned")
        .hasMessageContaining("groupB");
  }

  @Test
  void shouldMarkMemberAsLeaving() {
    // given
    final var initialConfig =
        globalConfigurationWith(Map.of(memberId, BrokerState.initializeAsActive()));
    final var applier = new MemberLeaveApplier(memberId, clusterMembershipChangeExecutor);

    // when
    final var result = applier.init(currentClusterConfigurationWith(initialConfig, Map.of()));

    // then
    assertThat(result).isRight();
    final var updatedConfig = result.get().apply(initialConfig);
    Assertions.assertThat(updatedConfig.getMember(memberId).state()).isEqualTo(State.LEAVING);
  }

  @Test
  void shouldExecuteLeaveCallback() {
    // given
    final var initialConfig =
        globalConfigurationWith(Map.of(memberId, BrokerState.initializeAsActive()));
    final var applier = new MemberLeaveApplier(memberId, clusterMembershipChangeExecutor);
    final var configWithLeaving =
        applier
            .init(currentClusterConfigurationWith(initialConfig, Map.of()))
            .get()
            .apply(initialConfig);

    // when
    final var resultingConfig = applier.apply().join().apply(configWithLeaving);

    // then
    Assertions.assertThat(resultingConfig.getMember(memberId).state()).isEqualTo(State.LEFT);
  }

  @Test
  void shouldReturnExceptionWhenRemoveBrokerFailed() {
    // given
    final var failingExecutor =
        new io.camunda.zeebe.dynamic.config.changes.ClusterMembershipChangeExecutor() {
          @Override
          public io.camunda.zeebe.scheduler.future.ActorFuture<Void> addBroker(
              final MemberId memberId) {
            return io.camunda.zeebe.scheduler.future.CompletableActorFuture.completed(null);
          }

          @Override
          public io.camunda.zeebe.scheduler.future.ActorFuture<Void> removeBroker(
              final MemberId memberId) {
            return io.camunda.zeebe.scheduler.future.CompletableActorFuture.completedExceptionally(
                new RuntimeException("Expected"));
          }
        };
    final var applier = new MemberLeaveApplier(memberId, failingExecutor);

    // when
    final var leaveFuture = applier.apply();

    // then
    Assertions.assertThat(leaveFuture)
        .failsWithin(java.time.Duration.ofMillis(100))
        .withThrowableOfType(java.util.concurrent.ExecutionException.class)
        .withCauseInstanceOf(RuntimeException.class)
        .withMessageContaining("Expected");
  }
}
