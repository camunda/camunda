/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scheduled.runtime;

import java.time.Duration;

/** Registration-time schedule for a {@link ScheduledTask}. */
public sealed interface Schedule {

  /** Run every {@code interval} after recovery; runtime may pull forward on hints/nudges. */
  record Periodic(Duration interval) implements Schedule {}

  /**
   * Only runs when nudged or a {@link Hint} requests it; {@code minDelay} is the floor between
   * runs.
   */
  record OnDemand(Duration minDelay) implements Schedule {}
}
