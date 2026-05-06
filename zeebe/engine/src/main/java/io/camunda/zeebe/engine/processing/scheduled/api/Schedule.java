/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scheduled.api;

import java.time.Duration;

/**
 * How a {@link ScheduledTask} should be scheduled. One value collapses the legacy "fixed-rate /
 * self-rescheduling / on-demand" patterns into a single configuration.
 *
 * @param fallbackInterval baseline interval used when {@link Outcome.Idle} is returned. {@code
 *     null} means pure on-demand: the task only fires when externally requested or when it returned
 *     {@link Outcome.AwaitDueAt}.
 * @param yieldBudget how long a single run may consume before {@link TaskContext#shouldYield()}
 *     returns {@code true}. {@code null} disables cooperative yielding.
 * @param minResolution lower bound on the gap between consecutive runs. The runtime never schedules
 *     earlier than {@code now + minResolution}, regardless of what the task requested. Prevents
 *     busy-looping when many entries are due at once.
 * @param async whether to run on the async scheduling actor instead of the stream processor's
 *     actor.
 */
public record Schedule(
    Duration fallbackInterval, Duration yieldBudget, Duration minResolution, boolean async) {

  public static final Duration DEFAULT_MIN_RESOLUTION = Duration.ofMillis(10);

  /** Periodic runs at {@code interval}; no yielding. */
  public static Schedule fixedRate(final Duration interval) {
    return new Schedule(interval, null, DEFAULT_MIN_RESOLUTION, false);
  }

  /** Pure on-demand: only runs when externally requested or via {@link Outcome.AwaitDueAt}. */
  public static Schedule onDemand(final Duration minResolution) {
    return new Schedule(null, null, minResolution, false);
  }

  /** On-demand with a fixed-interval safety net (i.e. wakes up at least every {@code fallback}). */
  public static Schedule onDemandWithFallback(
      final Duration fallback, final Duration minResolution) {
    return new Schedule(fallback, null, minResolution, false);
  }

  /** Same as {@link #fixedRate} but with cooperative yielding. */
  public Schedule withYieldBudget(final Duration budget) {
    return new Schedule(fallbackInterval, budget, minResolution, async);
  }

  public Schedule withAsync(final boolean asyncFlag) {
    return new Schedule(fallbackInterval, yieldBudget, minResolution, asyncFlag);
  }
}
