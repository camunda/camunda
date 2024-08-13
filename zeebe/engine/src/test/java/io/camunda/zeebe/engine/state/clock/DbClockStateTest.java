/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clock;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableClockState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbClockStateTest {
  private MutableProcessingState processingState;
  private MutableClockState state;

  @BeforeEach
  void beforeEach() {
    state = processingState.getClockState();
  }

  @Test
  void shouldDefaultToNone() {
    // given
    final var expected = Modification.none();

    // when
    final var actual = state.getModification();

    // then
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void shouldStoreModification() {
    // given
    final var modification = Modification.offsetBy(Duration.ofMinutes(10));

    // when
    state.offsetBy(modification.by().toMillis());

    // then
    assertThat(state.getModification()).isEqualTo(modification);
  }
}
