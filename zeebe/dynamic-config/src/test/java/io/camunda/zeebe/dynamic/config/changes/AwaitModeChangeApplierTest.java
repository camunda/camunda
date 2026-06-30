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

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import org.junit.jupiter.api.Test;

final class AwaitModeChangeApplierTest {

  private final ModeChangeExecutor executor = mock(ModeChangeExecutor.class);

  @Test
  void shouldInitWithoutChangingConfiguration() {
    // given
    final var applier = new AwaitModeChangeApplier(Mode.RECOVERING, executor);
    final var config = ClusterConfiguration.init();

    // when
    final var result = applier.init(config);

    // then - the await operation does not change cluster configuration state
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get().apply(config)).isEqualTo(config);
  }

  @Test
  void shouldCompleteWhenModeApplied() {
    // given
    when(executor.awaitModeApplied(Mode.RECOVERING))
        .thenReturn(CompletableActorFuture.completed(null));
    final var applier = new AwaitModeChangeApplier(Mode.RECOVERING, executor);

    // when
    final var result = applier.apply();

    // then - completes with an identity transformer (the await changes no configuration state)
    assertThat(result.isCompletedExceptionally()).isFalse();
    final var config = ClusterConfiguration.init();
    assertThat(result.join().apply(config)).isEqualTo(config);
  }

  @Test
  void shouldFailWhenAwaitFails() {
    // given
    when(executor.awaitModeApplied(Mode.PROCESSING))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new RuntimeException("not started")));
    final var applier = new AwaitModeChangeApplier(Mode.PROCESSING, executor);

    // when
    final var result = applier.apply();

    // then - a failed await is propagated so the cluster change is retried
    assertThat(result.isCompletedExceptionally()).isTrue();
  }
}
