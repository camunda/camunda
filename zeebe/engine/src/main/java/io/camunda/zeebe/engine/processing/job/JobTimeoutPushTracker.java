/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import org.agrona.collections.LongHashSet;

/**
 * Tracks which jobs have already been re-pushed after a timeout, to prevent the "vicious circle"
 * amplification pattern where repeated timeouts cause unbounded push growth.
 *
 * <p>Once a job has been re-pushed after timeout, subsequent timeouts for that job will use {@code
 * notifyJobAvailableAsSideEffect()} instead of {@code publishWork()}, letting workers pick up the
 * job via long-polling when they have capacity.
 *
 * <p>This state is transient (not persisted). On partition leader change or restart, the set is
 * recreated empty — jobs get one fresh direct re-push. This is acceptable because the tracking is a
 * best-effort optimization to limit amplification under backpressure.
 *
 * <p>Memory is bounded by {@code maxTrackedJobs}. When the set exceeds this limit, all entries are
 * cleared — jobs get one fresh direct re-push. This prevents unbounded memory growth from completed
 * jobs whose entries would otherwise never be cleaned up.
 *
 * <p>Uses Agrona's {@link LongHashSet} to store primitive longs without boxing overhead. The set
 * uses open addressing with a 0.65 load factor, so the backing {@code long[]} array is ~1.5x the
 * entry count (rounded to next power of 2). At the default limit of 100,000 entries, the backing
 * array is 262,144 longs = ~2 MB — significantly less than a boxed {@code HashSet<Long>} which
 * would use ~4-5 MB for the same number of entries.
 */
public final class JobTimeoutPushTracker {

  private static final int DEFAULT_MAX_TRACKED_JOBS = 100_000;

  private final LongHashSet pushedJobs = new LongHashSet();
  private final int maxTrackedJobs;

  public JobTimeoutPushTracker() {
    this(DEFAULT_MAX_TRACKED_JOBS);
  }

  public JobTimeoutPushTracker(final int maxTrackedJobs) {
    this.maxTrackedJobs = maxTrackedJobs;
  }

  /** Returns true if the job has not yet been re-pushed after a timeout. */
  public boolean shouldPush(final long jobKey) {
    return !pushedJobs.contains(jobKey);
  }

  /** Mark a job as having been re-pushed after timeout. */
  public void recordPush(final long jobKey) {
    if (pushedJobs.size() >= maxTrackedJobs) {
      // Clear all entries to bound memory. Jobs get one fresh direct re-push.
      // This is safe because the tracking is best-effort: the worst case is
      // a single extra direct re-push per job until it's tracked again.
      pushedJobs.clear();
    }
    pushedJobs.add(jobKey);
  }

  /** Remove tracking for a job (e.g., when the job is no longer activatable). */
  public void remove(final long jobKey) {
    pushedJobs.remove(jobKey);
  }

  /** Returns the number of currently tracked jobs. Intended for testing and metrics. */
  public int size() {
    return pushedJobs.size();
  }
}
