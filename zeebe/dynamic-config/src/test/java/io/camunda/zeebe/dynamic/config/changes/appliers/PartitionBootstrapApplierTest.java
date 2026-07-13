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
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PartitionBootstrapApplierTest {

  private final PartitionChangeExecutor partitionChangeExecutor =
      mock(PartitionChangeExecutor.class);
  private final MemberId memberId = MemberId.from("1");
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

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

  private static BrokerPartitionState brokerWith(final Map<Integer, PartitionState> partitions) {
    return new BrokerPartitionState(1, Instant.EPOCH, partitions, Mode.PROCESSING);
  }

  private PartitionBootstrapApplier applierFor(final int partitionId) {
    return new PartitionBootstrapApplier(
        new PartitionBootstrapOperation(memberId, partitionId, 1, false), partitionChangeExecutor);
  }

  @Test
  void shouldRejectIfLocalMemberIsNotActive() {
    // given
    final var initialGroup = groupWithMembers(Map.of());

    // when
    final var result = applierFor(1).init(globalConfigurationWith(Map.of()), initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not active");
  }

  @Test
  void shouldRejectIfPartitionAlreadyExistsInGroup() {
    // given
    final var initialGroup =
        groupWithMembers(
            Map.of(
                MemberId.from("2"),
                brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result = applierFor(1).init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already exists in the group");
  }

  @Test
  void shouldRejectIfPartitionIdIsNotContiguous() {
    // given — no existing partitions in the group, so only partition 1 is a valid next id
    final var initialGroup = groupWithMembers(Map.of());

    // when
    final var result = applierFor(2).init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not contiguous");
  }

  @Test
  void shouldNotFailOnInitIfAlreadyBootstrapping() {
    // given — restart-safe retry
    final var initialGroup =
        groupWithMembers(
            Map.of(
                memberId, brokerWith(Map.of(1, PartitionState.bootstrapping(1, partitionConfig)))));

    // when
    final var result = applierFor(1).init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isRight();
  }

  @Test
  void shouldAddNewMemberBootstrappingFirstPartition() {
    // given — the local member is new to this group entirely
    final var initialGroup = groupWithMembers(Map.of());

    // when
    final var updater =
        applierFor(1).init(globalConfigurationWithLocalMemberActive, initialGroup).get();
    final var resultingGroup = updater.apply(initialGroup);

    // then
    final var localPartition = resultingGroup.getMember(memberId).getPartition(1);
    Assertions.assertThat(localPartition.state()).isEqualTo(State.BOOTSTRAPPING);
  }

  @Test
  void shouldAddPartitionToExistingMemberOfTheGroup() {
    // given — the local member already replicates partition 1 in this group
    final var initialGroup =
        groupWithMembers(
            Map.of(memberId, brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var updater =
        applierFor(2).init(globalConfigurationWithLocalMemberActive, initialGroup).get();
    final var resultingGroup = updater.apply(initialGroup);

    // then
    final var localBroker = resultingGroup.getMember(memberId);
    Assertions.assertThat(localBroker.getPartition(1).state()).isEqualTo(State.ACTIVE);
    Assertions.assertThat(localBroker.getPartition(2).state()).isEqualTo(State.BOOTSTRAPPING);
  }

  @Test
  void shouldExecuteBootstrapCallback() {
    // given
    final var initialGroup = groupWithMembers(Map.of());
    final var applier = applierFor(1);
    final var groupWithBootstrapping =
        applier
            .init(globalConfigurationWithLocalMemberActive, initialGroup)
            .get()
            .apply(initialGroup);
    when(partitionChangeExecutor.bootstrap(anyInt(), anyInt(), any(), anyBoolean()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var resultingGroup = applier.apply().join().apply(groupWithBootstrapping);

    // then
    verify(partitionChangeExecutor, times(1)).bootstrap(1, 1, partitionConfig, false);
    Assertions.assertThat(resultingGroup.getMember(memberId).getPartition(1).state())
        .isEqualTo(State.ACTIVE);
  }
}
