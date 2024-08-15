/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification.None;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification.Offset;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification.Pin;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;

public final class ControllableStreamClockImpl implements StreamClock.ControllableStreamClock {

  private final InstantSource source;
  private Modification modification;

  /**
   * Internal field to optimize {@link #millis()} calls. For {@link Pin}, it stores the pinned time
   * in millis. For {@link Offset}, it stores the offset in millis. For {@link None}, it stores 0.
   */
  private long millis;

  public ControllableStreamClockImpl(final InstantSource source) {
    this.source = Objects.requireNonNull(source);
    reset();
  }

  @Override
  public void applyModification(final Modification modification) {
    this.modification = modification;
    millis =
        switch (modification) {
          case None() -> 0;
          case Offset(final var offset) -> offset.toMillis();
          case Pin(final var pinnedAt) -> pinnedAt.toEpochMilli();
        };
  }

  @Override
  public Modification currentModification() {
    return modification;
  }

  @Override
  public Instant instant() {
    return switch (modification) {
      case None() -> source.instant();
      case Offset(final var offset) -> source.instant().plus(offset);
      case Pin(final var pinnedAt) -> pinnedAt;
    };
  }

  @Override
  public long millis() {
    return switch (modification) {
      case final None ignored -> source.millis();
      case final Pin ignored -> millis;
      case final Offset ignored -> source.millis() + millis;
    };
  }

  @Override
  public int hashCode() {
    return Objects.hash(source, modification);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final ControllableStreamClockImpl that)) {
      return false;
    }
    return Objects.equals(source, that.source) && Objects.equals(modification, that.modification);
  }

  @Override
  public String toString() {
    return "ControllableStreamClock{" + "source=" + source + ", modification=" + modification + '}';
  }
}
