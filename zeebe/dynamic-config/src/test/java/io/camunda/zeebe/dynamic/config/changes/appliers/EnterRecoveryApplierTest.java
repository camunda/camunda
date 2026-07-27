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
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class EnterRecoveryApplierTest {

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
                memberId, new BrokerPartitionState(1, Instant.EPOCH, Map.of(), Mode.PROCESSING)));

    // when
    final var result =
        new EnterRecoveryApplier(memberId, modeChangeExecutor)
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
        new EnterRecoveryApplier(memberId, modeChangeExecutor)
            .init(globalConfigurationWithLocalMemberActive, group);

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not part of this partition group");
  }

  @Test
  void shouldExecuteEnterRecoveryCallbackAndSetModeRecovering() {
    // given
    final var group =
        groupWithMembers(
            Map.of(
                memberId, new BrokerPartitionState(1, Instant.EPOCH, Map.of(), Mode.PROCESSING)));
    final var applier = new EnterRecoveryApplier(memberId, modeChangeExecutor);
    when(modeChangeExecutor.enterRecovery()).thenReturn(CompletableActorFuture.completed(null));

    // when
    final var initResult = applier.init(globalConfigurationWithLocalMemberActive, group);
    assertThat(initResult).isRight();
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    verify(modeChangeExecutor, times(1)).enterRecovery();
    Assertions.assertThat(resultingGroup.getMember(memberId).mode()).isEqualTo(Mode.RECOVERING);
  }
}
