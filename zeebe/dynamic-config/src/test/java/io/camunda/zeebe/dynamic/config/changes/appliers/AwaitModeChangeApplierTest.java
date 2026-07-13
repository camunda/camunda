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

import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Map;
import java.util.Optional;
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
        new AwaitModeChangeApplier(Mode.RECOVERING, modeChangeExecutor)
            .init(globalConfiguration, group);

    // then
    assertThat(result).isRight();
  }

  @Test
  void shouldExecuteAwaitModeAppliedAsNoop() {
    // given
    final var applier = new AwaitModeChangeApplier(Mode.PROCESSING, modeChangeExecutor);
    when(modeChangeExecutor.awaitModeApplied(Mode.PROCESSING))
        .thenReturn(CompletableActorFuture.completed(null));

    // when
    final var resultingGroup = applier.apply().join().apply(group);

    // then
    verify(modeChangeExecutor, times(1)).awaitModeApplied(Mode.PROCESSING);
    Assertions.assertThat(resultingGroup).isEqualTo(group);
  }
}
