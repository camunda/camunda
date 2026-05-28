/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import java.time.Instant;
import java.time.InstantSource;

/** Test clock; tests advance it manually. */
public final class FakeClock implements InstantSource {
  private long now;

  public FakeClock(final long initialMillis) {
    now = initialMillis;
  }

  public void setNow(final long millis) {
    now = millis;
  }

  public void advanceBy(final long millis) {
    now += millis;
  }

  @Override
  public Instant instant() {
    return Instant.ofEpochMilli(now);
  }

  @Override
  public long millis() {
    return now;
  }
}
