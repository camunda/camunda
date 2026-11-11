/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.security.core.authz.TenantAccess;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;
import org.agrona.DirectBuffer;

public interface JobState {

  /**
   * Loops over all timed-out job entries and applies the provided callback.
   *
   * @param executionTimestamp Timestamp against which it's determined whether the deadline has
   *     expired
   * @param startAt Index used to start the iteration at; looping starts at the beginning when
   *     startAt is {@code null}
   * @param callback A callback method to be applied to each job entry. It must return a boolean
   *     that when {@code true} allows the loop to continue, or when {@code false} stops iteration.
   * @return The last visited index where the iteration has stopped because the {@code callback}
   *     method returned false or {@code null} if it was not the case.
   */
  DeadlineIndex forEachTimedOutEntry(
      long executionTimestamp, final DeadlineIndex startAt, BiPredicate<Long, JobRecord> callback);

  boolean exists(long jobKey);

  State getState(long key);

  boolean isInState(long key, State state);

  void forEachActivatableJobs(
      DirectBuffer type,
      final List<String> tenantIds,
      BiFunction<Long, JobRecord, Boolean> callback);

  /**
   * Like {@link #forEachActivatableJobs(DirectBuffer, List, BiFunction)}, telling the state how
   * many jobs the callback accepts at most, which the state may use to read ahead. The callback is
   * still called until it returns false or no job is left, as it may reject jobs.
   *
   * @param limitHint the maximum number of jobs the callback accepts
   */
  void forEachActivatableJobs(
      DirectBuffer type, List<String> tenantIds, int limitHint, ActivatableJobVisitor callback);

  JobRecord getJob(long key);

  JobRecord getJob(final long key, final TenantAccess authorizedTenantIds);

  boolean jobDeadlineExists(final long jobKey, final long deadline);

  long findBackedOffJobs(final long timestamp, final BiPredicate<Long, JobRecord> callback);

  /**
   * Visits every job key indexed under the given process instance, in key order. Not guaranteed to
   * find every job of the instance, since the index is only filled in at job creation (8.10+) and
   * on suspension.
   *
   * @param processInstanceKey the process instance whose jobs to visit
   * @param startAtJobKey the job key to start at (inclusive), or a negative value to start at the
   *     beginning
   * @param visitor called with each job key; returning {@code false} stops the iteration
   */
  void forEachJobsByProcessInstance(
      long processInstanceKey, long startAtJobKey, LongPredicate visitor);

  /**
   * Visits activatable jobs, see {@link #forEachActivatableJobs(DirectBuffer, List, int,
   * ActivatableJobVisitor)}: returns whether to continue with the next job.
   */
  @FunctionalInterface
  interface ActivatableJobVisitor extends BiFunction<Long, JobRecord, Boolean> {

    /**
     * Called by a state that reads the activatable jobs ahead, with the keys of the jobs it read,
     * in the order it visits them next, so the visitor can prefetch what it needs for them in one
     * go. The visitor may still stop before it has visited all of them. A state that doesn't read
     * ahead, like the RocksDB one, never calls it.
     */
    default void readAhead(final long[] jobKeys) {}
  }

  /** Index to point to a specific position in the jobs with deadline column family. */
  record DeadlineIndex(long deadline, long key) {}

  enum State {
    ACTIVATABLE((byte) 0),
    ACTIVATED((byte) 1),
    FAILED((byte) 2),
    NOT_FOUND((byte) 3),
    ERROR_THROWN((byte) 4),
    /**
     * The job is parked until its secret references are resolved in the background. It is not in
     * the activatable index, so it is handed out to no worker, and it leaves this state only by
     * being reactivated after the resolution or by being deleted.
     */
    WAITING_FOR_SECRET_RESOLUTION((byte) 5),
    /**
     * The job belongs to a suspended process instance. It is not in the activatable index, so it is
     * handed out to no worker, and it leaves this state only by being resumed with its process
     * instance or by being deleted.
     */
    SUSPENDED((byte) 6);

    private static final State[] BY_VALUE;

    static {
      int highestValue = 0;
      for (final State state : values()) {
        highestValue = Math.max(highestValue, state.value);
      }
      BY_VALUE = new State[highestValue + 1];
      for (final State state : values()) {
        BY_VALUE[state.value] = state;
      }
    }

    byte value;

    State(final byte value) {
      this.value = value;
    }

    /**
     * A stable code of the state, which stores may persist; unlike the ordinal, it never changes.
     */
    public byte value() {
      return value;
    }

    /**
     * @throws IllegalArgumentException if no state has the code
     */
    public static State fromValue(final byte value) {
      if (value < 0 || value >= BY_VALUE.length || BY_VALUE[value] == null) {
        throw new IllegalArgumentException("Expected a job state code, but got " + value);
      }
      return BY_VALUE[value];
    }
  }
}
