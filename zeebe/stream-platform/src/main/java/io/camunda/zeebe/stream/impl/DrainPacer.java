/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

/**
 * Paces one persist round's sliced drain against a deadline, Postgres checkpoint-spreading style:
 * after each slice the drain waits exactly long enough that its progress fraction never runs ahead
 * of {@code elapsed / budget}. A drain behind schedule (or past the budget) never waits — pacing
 * only ever slows a drain down that is ahead, so the round finishes by the deadline no matter how
 * the slices are sized.
 *
 * <p>The wait to resume at progress {@code p} solves {@code p == elapsed' / budget} for {@code
 * elapsed'}: resume at {@code p × budget}, i.e. wait {@code p × budget − elapsed}. Since {@code p ≤
 * 1}, the wait never extends past the budget itself.
 *
 * <p><b>Urgency hook:</b> {@link #expedite()} shortens the budget to zero mid-round, turning every
 * subsequent {@link #delayNanos(double, long)} into "no wait" — the owner pulls it when buffered
 * state runs over capacity while the round is in flight, so the drain finishes flat-out instead of
 * leisurely pacing while memory is under pressure.
 *
 * <p><b>Threading:</b> the drain thread calls {@link #delayNanos(double, long)}, the owner thread
 * may call {@link #expedite()} at any time; the budget is the only shared state and is volatile.
 */
final class DrainPacer {

  private final long startNanos;
  private volatile long budgetNanos;

  /**
   * @param budgetNanos the time budget of the whole drain, from {@code startNanos} on — typically
   *     {@code pacingTargetFraction × persistInterval}
   * @param startNanos the drain's start timestamp ({@code System.nanoTime()} domain)
   */
  DrainPacer(final long budgetNanos, final long startNanos) {
    if (budgetNanos < 0) {
      throw new IllegalArgumentException(
          "expected a non-negative pacing budget, but was " + budgetNanos);
    }
    this.budgetNanos = budgetNanos;
    this.startNanos = startNanos;
  }

  /**
   * How long the drain should wait before its next slice, given its progress fraction (in [0, 1])
   * at {@code nowNanos} — zero when it is on or behind schedule, expedited, or past the budget.
   */
  long delayNanos(final double progress, final long nowNanos) {
    final long budget = budgetNanos;
    final long elapsed = nowNanos - startNanos;
    if (elapsed >= budget) {
      return 0;
    }
    final long resumeAt = (long) (progress * budget);
    return Math.max(0, resumeAt - elapsed);
  }

  /** Drops the budget to zero: every subsequent delay is zero and the drain runs flat-out. */
  void expedite() {
    budgetNanos = 0;
  }
}
