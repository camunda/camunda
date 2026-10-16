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
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class AwaitModeChangeApplierTest {

  private final ModeChangeExecutor modeChangeExecutor = mock(ModeChangeExecutor.class);
  private final GlobalConfiguration globalConfiguration = GlobalConfiguration.init();
  private final PartitionGroupConfiguration group =
      new PartitionGroupConfiguration(
          1, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.empty());

  @Test
  void shouldAlwaysAcceptOnInit() {
    // when
    final var result =
        new AwaitModeChangeApplier(MemberId.from(0), Mode.RECOVERING, modeChangeExecutor)
            .init(globalConfiguration, group);

    // then
    assertThat(result).isRight();
  }

  @Test
  void shouldExecuteAwaitModeAppliedAsNoop() {
    // given
    final var applier =
        new AwaitModeChangeApplier(MemberId.from(0), Mode.PROCESSING, modeChangeExecutor);
    when(modeChangeExecutor.awaitModeApplied(Mode.PROCESSING))
        .thenReturn(CompletableActorFuture.completed(Set.of()));

    // when
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    verify(modeChangeExecutor, times(1)).awaitModeApplied(Mode.PROCESSING);
    Assertions.assertThat(resultingGroup).isEqualTo(group);
  }

  @Test
  void shouldWriteRecoveringForConfirmedPartitionsOnly() {
    // given
    final var memberId = MemberId.from(0);
    final var partitionConfig = DynamicPartitionConfig.init();
    final var groupWithMember =
        group.addMember(
            memberId,
            BrokerPartitionState.initialize(
                Map.of(
                    1, PartitionState.active(1, partitionConfig),
                    2, PartitionState.active(1, partitionConfig))));
    final var applier = new AwaitModeChangeApplier(memberId, Mode.RECOVERING, modeChangeExecutor);
    when(modeChangeExecutor.awaitModeApplied(Mode.RECOVERING))
        .thenReturn(CompletableActorFuture.completed(Set.of(1)));

    // when
    final var resultingGroup = applier.apply().join().apply(groupWithMember);

    // then - only the confirmed partition (1) is marked RECOVERING; partition 2 (e.g. dead) is
    // left untouched
    Assertions.assertThat(resultingGroup.getMember(memberId).getPartition(1).state())
        .isEqualTo(PartitionState.State.RECOVERING);
    Assertions.assertThat(resultingGroup.getMember(memberId).getPartition(2).state())
        .isEqualTo(PartitionState.State.ACTIVE);
  }

  @Test
  void shouldWriteActiveForConfirmedPartitionsWhenExitingRecovery() {
    // given
    final var memberId = MemberId.from(0);
    final var partitionConfig = DynamicPartitionConfig.init();
    final var groupWithMember =
        group.addMember(
            memberId,
            BrokerPartitionState.initialize(
                Map.of(1, PartitionState.active(1, partitionConfig).toRecovering())));
    final var applier = new AwaitModeChangeApplier(memberId, Mode.PROCESSING, modeChangeExecutor);
    when(modeChangeExecutor.awaitModeApplied(Mode.PROCESSING))
        .thenReturn(CompletableActorFuture.completed(Set.of(1)));

    // when
    final var resultingGroup = applier.apply().join().apply(groupWithMember);

    // then
    Assertions.assertThat(resultingGroup.getMember(memberId).getPartition(1).state())
        .isEqualTo(PartitionState.State.ACTIVE);
  }

  @Test
  void shouldFailWhenAwaitFails() {
    // given
    final var applier =
        new AwaitModeChangeApplier(MemberId.from(0), Mode.PROCESSING, modeChangeExecutor);
    when(modeChangeExecutor.awaitModeApplied(Mode.PROCESSING))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("not started")));

    // when
    final var result = applier.apply();

    // then - a failed await is propagated so the cluster change is retried
    Assertions.assertThat(result.isCompletedExceptionally()).isTrue();
  }
}
