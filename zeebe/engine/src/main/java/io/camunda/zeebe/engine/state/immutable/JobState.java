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

    byte value;

    State(final byte value) {
      this.value = value;
    }
  }
}
