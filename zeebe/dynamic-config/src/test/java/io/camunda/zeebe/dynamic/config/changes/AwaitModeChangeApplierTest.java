/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class AwaitModeChangeApplierTest {

  private static final MemberId MEMBER_ID = MemberId.from("1");
  private final ModeChangeExecutor executor = mock(ModeChangeExecutor.class);

  @Test
  void shouldInitWithoutChangingConfiguration() {
    // given
    final var applier = new AwaitModeChangeApplier(MEMBER_ID, Mode.RECOVERING, executor);
    final var config = ClusterConfiguration.init();

    // when
    final var result = applier.init(config);

    // then - the await operation does not change cluster configuration state on init
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get().apply(config)).isEqualTo(config);
  }

  @Test
  void shouldWriteRecoveringForConfirmedPartitionsOnly() {
    // given
    final var partitionConfig = DynamicPartitionConfig.init();
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MEMBER_ID,
                MemberState.initializeAsActive(
                    Map.of(
                        1, PartitionState.active(1, partitionConfig),
                        2, PartitionState.active(1, partitionConfig))));
    when(executor.awaitModeApplied(Mode.RECOVERING))
        .thenReturn(CompletableActorFuture.completed(Set.of(1)));
    final var applier = new AwaitModeChangeApplier(MEMBER_ID, Mode.RECOVERING, executor);

    // when
    final var result = applier.apply();

    // then - only the confirmed partition (1) is marked RECOVERING; partition 2 (e.g. dead) is
    // left untouched
    assertThat(result.isCompletedExceptionally()).isFalse();
    final var updated = result.join().apply(config);
    assertThat(updated.getMember(MEMBER_ID).getPartition(1).state())
        .isEqualTo(PartitionState.State.RECOVERING);
    assertThat(updated.getMember(MEMBER_ID).getPartition(2).state())
        .isEqualTo(PartitionState.State.ACTIVE);
  }

  @Test
  void shouldWriteActiveForConfirmedPartitionsWhenExitingRecovery() {
    // given
    final var partitionConfig = DynamicPartitionConfig.init();
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MEMBER_ID,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig).toRecovering())));
    when(executor.awaitModeApplied(Mode.PROCESSING))
        .thenReturn(CompletableActorFuture.completed(Set.of(1)));
    final var applier = new AwaitModeChangeApplier(MEMBER_ID, Mode.PROCESSING, executor);

    // when
    final var result = applier.apply();

    // then
    assertThat(result.isCompletedExceptionally()).isFalse();
    final var updated = result.join().apply(config);
    assertThat(updated.getMember(MEMBER_ID).getPartition(1).state())
        .isEqualTo(PartitionState.State.ACTIVE);
  }

  @Test
  void shouldNotChangeConfigurationWhenNoPartitionsConfirmed() {
    // given - e.g. the only local partition ended up DEAD
    final var config = ClusterConfiguration.init();
    when(executor.awaitModeApplied(Mode.RECOVERING))
        .thenReturn(CompletableActorFuture.completed(Set.of()));
    final var applier = new AwaitModeChangeApplier(MEMBER_ID, Mode.RECOVERING, executor);

    // when
    final var result = applier.apply();

    // then
    assertThat(result.isCompletedExceptionally()).isFalse();
    assertThat(result.join().apply(config)).isEqualTo(config);
  }

  @Test
  void shouldFailWhenAwaitFails() {
    // given
    when(executor.awaitModeApplied(Mode.PROCESSING))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("not started")));
    final var applier = new AwaitModeChangeApplier(MEMBER_ID, Mode.PROCESSING, executor);

    // when
    final var result = applier.apply();

    // then - a failed await is propagated so the cluster change is retried
    assertThat(result.isCompletedExceptionally()).isTrue();
  }
}
