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
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class PartitionReconfigurePriorityApplierTest {

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
  void shouldRejectIfLocalMemberIsNotActiveInCluster() {
    // given
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId, brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when
    final var result =
        new PartitionReconfigurePriorityApplier(localMemberId, 1, 3, partitionChangeExecutor)
            .init(globalConfigurationWith(Map.of()), initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the local member is not active");
  }

  @Test
  void shouldRejectIfLocalMemberDoesNotHavePartition() {
    // given
    final var initialGroup = groupWithMembers(Map.of(localMemberId, brokerWith(Map.of())));

    // when
    final var result =
        new PartitionReconfigurePriorityApplier(localMemberId, 1, 3, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not have the partition");
  }

  @Test
  void shouldRejectIfPartitionIsNotActive() {
    // given
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId, brokerWith(Map.of(1, PartitionState.joining(1, partitionConfig)))));

    // when
    final var result =
        new PartitionReconfigurePriorityApplier(localMemberId, 1, 3, partitionChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, initialGroup);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("has partition in state");
  }

  @Test
  void shouldExecuteReconfigurePriorityCallbackAndUpdatePriority() {
    // given
    final var initialGroup =
        groupWithMembers(
            Map.of(
                localMemberId, brokerWith(Map.of(1, PartitionState.active(1, partitionConfig)))));
    final var applier =
        new PartitionReconfigurePriorityApplier(localMemberId, 1, 5, partitionChangeExecutor);
    applier.init(globalConfigurationWithLocalMemberActive, initialGroup);
    when(partitionChangeExecutor.reconfigurePriority(anyInt(), anyInt()))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var resultingGroup = applier.apply().join().apply(initialGroup);

    // then
    verify(partitionChangeExecutor, times(1)).reconfigurePriority(1, 5);
    final var localPartition = resultingGroup.getMember(localMemberId).getPartition(1);
    Assertions.assertThat(localPartition.priority()).isEqualTo(5);
    Assertions.assertThat(localPartition.state()).isEqualTo(PartitionState.State.ACTIVE);
  }
}
