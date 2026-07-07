/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

/**
 * The composite lifecycle accumulator: per-transition counts (activated / completed / terminated)
 * <em>and</em> the duration {@link ExecutionTimeSummary} of the ended events, folded from one
 * transition-tagged fact stream. So "how many instances/elements were activated / completed /
 * terminated" and the duration stats (count/avg/min/max/percentiles) all come from a single cell —
 * the "one composite measure" idea extended across the lifecycle. Every field merges
 * commutatively/associatively, so it pre-aggregates across partitions.
 */
public final class LifecycleSummary {

  private long activated;
  private long completed;
  private long terminated;
  private final ExecutionTimeSummary duration;

  public LifecycleSummary(
      final long activated,
      final long completed,
      final long terminated,
      final ExecutionTimeSummary duration) {
    this.activated = activated;
    this.completed = completed;
    this.terminated = terminated;
    this.duration = duration;
  }

  public static LifecycleSummary empty() {
    return new LifecycleSummary(0L, 0L, 0L, ExecutionTimeSummary.empty());
  }

  public void recordActivated() {
    activated++;
  }

  public void recordCompleted() {
    completed++;
  }

  public void recordTerminated() {
    terminated++;
  }

  /** Folds an ended event's duration into the summary (completed/terminated carry a duration). */
  public void recordDuration(final long durationMs) {
    duration.record(durationMs);
  }

  /**
   * Folds {@code other} into this summary in place — the mutating counterpart of {@link
   * #merge(LifecycleSummary, LifecycleSummary)} for a summary this caller owns. {@code other} is
   * not modified, so it may be a read-only decoded view.
   */
  public void merge(final LifecycleSummary other) {
    activated += other.activated;
    completed += other.completed;
    terminated += other.terminated;
    duration.merge(other.duration);
  }

  public static LifecycleSummary merge(final LifecycleSummary a, final LifecycleSummary b) {
    return new LifecycleSummary(
        a.activated + b.activated,
        a.completed + b.completed,
        a.terminated + b.terminated,
        ExecutionTimeSummary.merge(a.duration, b.duration));
  }

  public long activated() {
    return activated;
  }

  public long completed() {
    return completed;
  }

  public long terminated() {
    return terminated;
  }

  public ExecutionTimeSummary duration() {
    return duration;
  }
}
