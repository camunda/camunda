/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

public class ControlledInstantSource implements InstantSource {

  private Instant instant;

  public ControlledInstantSource(final Instant initial) {
    instant = initial;
  }

  public void setInstant(final Instant instant) {
    this.instant = instant;
  }

  public void advance(final Duration duration) {
    instant = instant.plus(duration);
  }

  public void advance(final long millis) {
    instant = instant.plusMillis(millis);
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
