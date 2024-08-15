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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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

  @ParameterizedTest
  @MethodSource("provideModifications")
  void shouldStoreModification(final TestCase testCase) {
    // given
    final var modification = testCase.expected();
    state.pinAt(Instant.now().plusSeconds(3600).toEpochMilli());

    // when
    testCase.applyModification(state);

    // then
    assertThat(state.getModification()).isEqualTo(modification);
  }

  private static Stream<Named<TestCase>> provideModifications() {
    final var pinnedAt = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MILLIS);
    final var offsetBy = Duration.ofMinutes(5);
    return Stream.of(
        Named.named("none", new TestCase.None()),
        Named.named("pin", new TestCase.Pin(pinnedAt)),
        Named.named("offset", new TestCase.Offset(offsetBy)));
  }

  private sealed interface TestCase {
    Modification expected();

    void applyModification(final MutableClockState state);

    record None() implements TestCase {

      @Override
      public Modification expected() {
        return Modification.none();
      }

      @Override
      public void applyModification(final MutableClockState state) {
        state.reset();
      }
    }

    record Pin(Instant at) implements TestCase {

      @Override
      public Modification expected() {
        return Modification.pinAt(at);
      }

      @Override
      public void applyModification(final MutableClockState state) {
        state.pinAt(at.toEpochMilli());
      }
    }

    record Offset(Duration offset) implements TestCase {

      @Override
      public Modification expected() {
        return Modification.offsetBy(offset);
      }

      @Override
      public void applyModification(final MutableClockState state) {
        state.offsetBy(offset.toMillis());
      }
    }
  }
}
