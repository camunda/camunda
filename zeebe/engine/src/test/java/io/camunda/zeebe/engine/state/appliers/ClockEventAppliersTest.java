/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableClockState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class ClockEventAppliersTest {
  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableClockState state;

  @BeforeEach
  void beforeEach() {
    state = processingState.getClockState();
  }

  @Nested
  final class PinnedTest {
    private ClockPinnedApplier applier;

    @BeforeEach
    void beforeEach() {
      applier = new ClockPinnedApplier(state);
    }

    @Test
    void shouldPinClock() {
      // given
      final var clock = new ClockRecord().pinAt(5);

      // when
      applier.applyState(1, clock);

      // then
      assertThat(state.getModification()).isEqualTo(Modification.pinAt(Instant.ofEpochMilli(5)));
    }
  }
}
