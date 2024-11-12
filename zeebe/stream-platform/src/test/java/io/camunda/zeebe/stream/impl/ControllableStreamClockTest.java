/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class ControllableStreamClockTest {
  @Test
  void shouldStartWithoutModification() {
    // given
    final var now = Instant.now();
    final var source = InstantSource.fixed(now);
    final var clock = StreamClock.controllable(source);

    // then
    assertThat(clock.isModified()).isFalse();
    assertThat(clock.currentModification()).isInstanceOf(Modification.None.class);
  }

  @Test
  void shouldReturnSourceTimeWhenNotModified() {
    // given
    final var sourceTime = Instant.now();
    final var source = InstantSource.fixed(sourceTime);
    final var clock = StreamClock.controllable(source);

    // then
    assertThat(clock.instant()).isEqualTo(sourceTime);
    assertThat(clock.millis()).isEqualTo(sourceTime.toEpochMilli());
  }

  @Test
  void shouldReturnPinnedTime() {
    // given
    final var sourceTime = Instant.now();
    final var source = InstantSource.fixed(sourceTime);
    final var clock = StreamClock.controllable(source);
    final var pinnedTime = Instant.now().plusSeconds(10);

    // when
    clock.applyModification(Modification.pinAt(pinnedTime));

    // then
    assertThat(clock.instant()).isEqualTo(pinnedTime);
  }

  @Test
  void shouldReturnLastPinned() {
    // given
    final var sourceTime = Instant.now();
    final var source = InstantSource.fixed(sourceTime);
    final var clock = StreamClock.controllable(source);
    final var firstPin = Instant.now().plusSeconds(5);
    final var secondPin = Instant.now().plusSeconds(10);

    // when
    clock.applyModification(Modification.pinAt(firstPin));
    clock.applyModification(Modification.pinAt(secondPin));

    // then
    assertThat(clock.instant()).isEqualTo(secondPin);
  }

  @Test
  void shouldReturnOffsetTime() {
    // given
    final var sourceTime = Instant.now();
    final var source = InstantSource.fixed(sourceTime);
    final var clock = StreamClock.controllable(source);
    final var offset = Duration.ofSeconds(10);

    // when
    clock.applyModification(Modification.offsetBy(offset));

    // then
    assertThat(clock.instant()).isEqualTo(sourceTime.plus(offset));
    assertThat(clock.millis()).isEqualTo(sourceTime.toEpochMilli() + offset.toMillis());
  }

  @Test
  void shouldReturnNegativeOffsetTime() {
    // given
    final var sourceTime = Instant.now();
    final var source = InstantSource.fixed(sourceTime);
    final var clock = StreamClock.controllable(source);
    final var offset = Duration.ofSeconds(-10);

    // when
    clock.applyModification(Modification.offsetBy(offset));

    // then
    assertThat(clock.instant()).isEqualTo(sourceTime.plus(offset));
    assertThat(clock.millis()).isEqualTo(sourceTime.toEpochMilli() + offset.toMillis());
  }

  @Test
  void shouldReturnLastOffset() {
    // given
    final var sourceTime = Instant.now();
    final var source = InstantSource.fixed(sourceTime);
    final var clock = StreamClock.controllable(source);
    final var firstOffset = Duration.ofSeconds(5);
    final var secondOffset = Duration.ofSeconds(10);

    // when
    clock.applyModification(Modification.offsetBy(firstOffset));
    clock.applyModification(Modification.offsetBy(secondOffset));

    // then
    assertThat(clock.instant()).isEqualTo(sourceTime.plus(secondOffset));
  }

  @Test
  void shouldResetToSourceTime() {
    // given
    final var sourceTime = Instant.now();
    final var source = InstantSource.fixed(sourceTime);
    final var clock = StreamClock.controllable(source);

    // when -- offset and then reset
    final var offset = Duration.ofSeconds(10);
    clock.applyModification(Modification.offsetBy(offset));
    clock.reset();

    // then -- should return source time without offset
    assertThat(clock.instant()).isEqualTo(sourceTime);
    assertThat(clock.millis()).isEqualTo(sourceTime.toEpochMilli());

    // when -- pin and then reset
    final var pinnedTime = Instant.now().plusSeconds(10);
    clock.applyModification(Modification.pinAt(pinnedTime));
    clock.reset();

    // then -- should return source time without pin
    assertThat(clock.instant()).isEqualTo(sourceTime);
    assertThat(clock.millis()).isEqualTo(sourceTime.toEpochMilli());
  }

  @Test
  void shouldStackOffset() {
    // given
    final var sourceTime = Instant.now();
    final var source = InstantSource.fixed(sourceTime);
    final var clock = StreamClock.controllable(source);

    // when
    final var firstOffset = Duration.ofSeconds(5);
    final var secondOffset = Duration.ofSeconds(10);
    clock.offsetBy(firstOffset);
    clock.stackOffset(secondOffset);

    // then
    assertThat(clock.instant()).isEqualTo(sourceTime.plus(firstOffset).plus(secondOffset));
    assertThat(clock.millis())
        .isEqualTo(sourceTime.toEpochMilli() + firstOffset.toMillis() + secondOffset.toMillis());
  }
}
