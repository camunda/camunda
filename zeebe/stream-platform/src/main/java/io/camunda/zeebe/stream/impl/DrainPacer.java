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
 * state runs over capacity while the round is in flight, or when a consumer (snapshot flush,
 * scheduled-task barrier) starts awaiting the round's completion, so the drain finishes flat-out
 * instead of leisurely pacing while someone needs it done. Shortening the budget alone cannot
 * interrupt a wait the drain <em>already</em> scheduled — one wait can span most of the pacing
 * window — so {@link #expedite()} additionally fires the driver's {@link #onExpedite(Runnable)}
 * hook, which cancels the pending wait and resumes the drain right away.
 *
 * <p><b>Threading:</b> the drain thread calls {@link #delayNanos(double, long)} and registers the
 * {@link #onExpedite(Runnable)} hook, the owner thread may call {@link #expedite()} at any time;
 * budget and hook are the only shared state and both are volatile. An expedite that races the
 * hook's registration is never lost: the budget drops first, so a driver registering afterwards
 * computes every delay against the zero budget and schedules no wait to begin with.
 */
final class DrainPacer {

  private final long startNanos;
  private volatile long budgetNanos;
  private volatile Runnable expediteListener;

  /**
   * @param budgetNanos the time budget of the whole drain, from {@code startNanos} on — typically
   *     the configured pacing window; the deadline only shapes disk amplitude, correctness never
   *     depends on it
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

  /**
   * Registers the drain driver's wake-up hook, invoked on every {@link #expedite()} so a pacing
   * wait the driver already scheduled can be cancelled instead of sat out. May be invoked from the
   * owner thread at any time, possibly more than once and possibly while no wait is pending — the
   * hook must tolerate both (a resume with nothing pending is a no-op).
   */
  void onExpedite(final Runnable listener) {
    expediteListener = listener;
  }

  /**
   * Drops the budget to zero — every subsequent delay is zero and the drain runs flat-out — and
   * wakes the driver so an already-scheduled wait is cancelled rather than sat out.
   */
  void expedite() {
    budgetNanos = 0;
    final Runnable listener = expediteListener;
    if (listener != null) {
      listener.run();
    }
  }
}
