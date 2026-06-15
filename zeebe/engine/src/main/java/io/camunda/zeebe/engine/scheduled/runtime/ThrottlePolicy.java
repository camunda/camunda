/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import java.time.Duration;

/** Bound on how often a task may run. */
public sealed interface ThrottlePolicy {

  static ThrottlePolicy none() {
    return new None();
  }

  static ThrottlePolicy minInterval(final Duration minInterval) {
    return new MinInterval(minInterval);
  }

  /** No throttle. */
  record None() implements ThrottlePolicy {}

  /** Minimum elapsed time between two runs. */
  record MinInterval(Duration minInterval) implements ThrottlePolicy {}
}
