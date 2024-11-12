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
  private volatile Modification modification;

  public ControllableStreamClockImpl(final InstantSource source) {
    this.source = Objects.requireNonNull(source);
    reset();
  }

  @Override
  public void applyModification(final Modification modification) {
    this.modification = modification;
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
      case None() -> source.millis();
      case Pin(final var at) -> at.toEpochMilli();
      case Offset(final var offset) -> source.millis() + offset.toMillis();
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
