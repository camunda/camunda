/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

/** A clock that only moves when a test moves it. */
final class MutableInstantSource implements InstantSource {

  private Instant now = Instant.parse("2026-01-01T00:00:00Z");

  @Override
  public synchronized Instant instant() {
    return now;
  }

  synchronized void advance(final Duration duration) {
    now = now.plus(duration);
  }
}
