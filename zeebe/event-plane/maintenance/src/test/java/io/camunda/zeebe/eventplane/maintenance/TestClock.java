/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.temporal.ChronoUnit;

/**
 * A clock that stands still until a test moves it. It starts at the real time, as the ULIDs that
 * maintenance makes for its own objects hold the real time.
 */
final class TestClock implements InstantSource {

  private Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

  @Override
  public Instant instant() {
    return now;
  }

  void advance(final Duration duration) {
    now = now.plus(duration);
  }
}
