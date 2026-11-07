/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import java.time.Duration;

/** A manually driven clock whose sleeps advance time instead of blocking. */
final class FakeClock {

  private long nanos;
  private long sleptNanos;

  long nanos() {
    return nanos;
  }

  void sleep(final long sleepNanos) {
    nanos += sleepNanos;
    sleptNanos += sleepNanos;
  }

  void advance(final Duration duration) {
    nanos += duration.toNanos();
  }

  Duration slept() {
    return Duration.ofNanos(sleptNanos);
  }
}
