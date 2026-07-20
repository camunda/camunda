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
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PartitionForceReconfigureApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId member1 = MemberId.from("1");
  private final MemberId member2 = MemberId.from("2");
  private final MemberId member3 = MemberId.from("3");
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

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
  void shouldRejectIfNewMembersIsEmpty() {
    // given
    final var group = groupWithMembers(Map.of());

    // when
    final var result =
        new PartitionForceReconfigureApplier(member1, 1, List.of(), partitionChangeExecutor)
            .init(globalConfigurationWith(Map.of()), group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the new configuration is empty");
  }

  @Test
  void shouldRejectIfApplyingMemberNotInNewConfiguration() {
    // given
    final var group = groupWithMembers(Map.of());

    // when
    final var result =
        new PartitionForceReconfigureApplier(member1, 1, List.of(member2), partitionChangeExecutor)
            .init(globalConfigurationWith(Map.of()), group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not part of the new configuration");
  }

  @Test
  void shouldRejectIfAMemberIsNotActive() {
    // given
    final var group =
        groupWithMembers(
            Map.of(
                member1,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig))),
                member2,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));
    final var globalConfig =
        globalConfigurationWith(Map.of(member1, BrokerState.initializeAsActive()));

    // when
    final var result =
        new PartitionForceReconfigureApplier(
                member1, 1, List.of(member1, member2), partitionChangeExecutor)
            .init(globalConfig, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not active");
  }

  @Test
  void shouldRejectIfAMemberDoesNotHaveThePartition() {
    // given
    final var group = groupWithMembers(Map.of(member1, brokerWith(Map.of())));
    final var globalConfig =
        globalConfigurationWith(
            Map.of(
                member1, BrokerState.initializeAsActive(),
                member2, BrokerState.initializeAsActive()));

    // when
    final var result =
        new PartitionForceReconfigureApplier(
                member1, 1, List.of(member1, member2), partitionChangeExecutor)
            .init(globalConfig, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not have the partition");
  }

  @Test
  void shouldExecuteForceReconfigureAndRemovePartitionFromNonMembers() {
    // given — member3 currently has the partition but is not in the new configuration
    final var group =
        groupWithMembers(
            Map.of(
                member1,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig))),
                member2,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig))),
                member3,
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));
    final var globalConfig =
        globalConfigurationWith(
            Map.of(
                member1, BrokerState.initializeAsActive(),
                member2, BrokerState.initializeAsActive()));
    final var applier =
        new PartitionForceReconfigureApplier(
            member1, 1, List.of(member1, member2), partitionChangeExecutor);
    when(partitionChangeExecutor.forceReconfigure(anyInt(), any()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var initResult = applier.init(globalConfig, group);
    assertThat(initResult).isRight();
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    verify(partitionChangeExecutor, times(1)).forceReconfigure(1, List.of(member1, member2));
    Assertions.assertThat(resultingGroup.getMember(member3).hasPartition(1)).isFalse();
    Assertions.assertThat(resultingGroup.getMember(member1).hasPartition(1)).isTrue();
    Assertions.assertThat(resultingGroup.getMember(member2).hasPartition(1)).isTrue();
  }
}
