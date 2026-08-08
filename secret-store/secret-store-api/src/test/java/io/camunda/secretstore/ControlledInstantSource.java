/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

/** A mutable {@link InstantSource} test double, advanced explicitly instead of sleeping. */
final class ControlledInstantSource implements InstantSource {

  private Instant instant;

  ControlledInstantSource(final Instant instant) {
    this.instant = instant;
  }

  void advance(final Duration duration) {
    instant = instant.plus(duration);
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
