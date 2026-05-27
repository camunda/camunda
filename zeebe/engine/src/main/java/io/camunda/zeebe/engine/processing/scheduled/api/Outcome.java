/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.api;

/**
 * What a {@link ScheduledTask} tells the runtime about when it should fire next. The runtime is
 * free to clamp this to the configured {@code minResolution} to avoid busy-looping.
 */
public sealed interface Outcome {

  /** Singleton instance for {@link Idle}. */
  Idle IDLE = new Idle();

  /** Singleton instance for {@link YieldNow}. */
  YieldNow YIELD_NOW = new YieldNow();

  /** Whether the runtime should reschedule immediately, ignoring the fallback interval. */
  default boolean reschedulesImmediately() {
    return this instanceof YieldNow;
  }

  /**
   * Done. If a fallback interval is configured the runtime reschedules at {@code now + fallback};
   * for pure on-demand schedules the runtime sleeps until externally re-triggered.
   */
  record Idle() implements Outcome {}

  /**
   * The task gave up control mid-iteration (typically because the time budget elapsed or the result
   * batch is full). The runtime reschedules immediately.
   */
  record YieldNow() implements Outcome {}

  /**
   * Schedule the next run at the given absolute timestamp (millis since epoch). Useful for
   * on-demand checkers like timer or job-backoff that know exactly when their next entry is due.
   */
  record AwaitDueAt(long timestampMs) implements Outcome {}
}
