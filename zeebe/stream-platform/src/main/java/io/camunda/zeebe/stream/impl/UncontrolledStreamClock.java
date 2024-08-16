/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import java.time.Clock;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;
import java.util.Objects;

public final class UncontrolledStreamClock implements StreamClock {
  private final InstantSource source;

  public UncontrolledStreamClock(final InstantSource source) {
    this.source = Objects.requireNonNull(source);
  }

  @Override
  public Modification currentModification() {
    return Modification.none();
  }

  @Override
  public Instant instant() {
    return source.instant();
  }

  @Override
  public long millis() {
    return source.millis();
  }

  @Override
  public Clock withZone(final ZoneId zone) {
    return source.withZone(zone);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(source);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final UncontrolledStreamClock that)) {
      return false;
    }
    return Objects.equals(source, that.source);
  }

  @Override
  public String toString() {
    return "UncontrolledStreamClock{" + "source=" + source + '}';
  }
}
