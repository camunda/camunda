/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clock;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class DbClockModificationTest {
  @ParameterizedTest
  @MethodSource("provideModifications")
  void shouldSerializeModification(final TestCase testCase) {
    // given
    final var buffer = new ExpandableArrayBuffer();
    final var writer = new DbClockModification();
    final var reader = new DbClockModification();
    testCase.applyModification(writer);

    // when
    writer.write(buffer, 0);
    reader.wrap(buffer, 0, buffer.capacity());

    // then
    assertThat(reader.modification()).isEqualTo(testCase.expected());
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

    void applyModification(final DbClockModification modification);

    record None() implements TestCase {

      @Override
      public Modification expected() {
        return Modification.none();
      }

      @Override
      public void applyModification(final DbClockModification modification) {
        modification.reset();
      }
    }

    record Pin(Instant at) implements TestCase {

      @Override
      public Modification expected() {
        return Modification.pinAt(at);
      }

      @Override
      public void applyModification(final DbClockModification modification) {
        modification.pinAt(at.toEpochMilli());
      }
    }

    record Offset(Duration offset) implements TestCase {

      @Override
      public Modification expected() {
        return Modification.offsetBy(offset);
      }

      @Override
      public void applyModification(final DbClockModification modification) {
        modification.offsetBy(offset.toMillis());
      }
    }
  }
}
