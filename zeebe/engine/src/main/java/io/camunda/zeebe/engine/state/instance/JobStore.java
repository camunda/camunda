/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.List;
import java.util.function.LongPredicate;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Persistence of the job state: the jobs with their state, and the lookups to find them by
 * activation (type, priority, tenant), deadline, backoff and process instance. It holds none of the
 * domain rules of {@link JobStateImpl}, which decides on every transition which lookups a job is
 * entered in or removed from.
 *
 * <p>A lookup entry is identified by the job key and the indexed value (the deadline, the backoff,
 * or the type, tenant and priority of the activation). Clearing an entry with a value it isn't
 * stored under leaves the entry in place.
 *
 * <p>Unless documented otherwise, returned objects may be backed by buffers that the store reuses
 * on its next read: callers must not keep them across another read of the store. Visitors must not
 * write to the lookup they visit, unless documented otherwise.
 */
public interface JobStore {

  /** Passed as limit hint to {@link #visitActivatableJobs} when the caller knows no limit. */
  int NO_LIMIT_HINT = -1;

  /**
   * @return the job without variables, a shared object that the next read overwrites, or null if
   *     there is none
   */
  @Nullable JobRecord getJob(long key);

  /**
   * @return the state of the job, or null if there is no job
   */
  @Nullable State getState(long key);

  boolean exists(long key);

  /** Inserts a new job, without its variables, in the given state. */
  void insertJob(long key, JobRecord record, State state);

  /** Updates the existing job, without its variables. */
  void updateJob(long key, JobRecord record);

  void updateState(long key, State state);

  /** Deletes the existing job and its state, but none of its lookup entries. */
  void deleteJob(long key);

  /**
   * Enters the job for activation by workers of the type, in the order of descending priority and
   * then ascending job key. Setting the same entry again is allowed.
   */
  void setActivatable(long key, DirectBuffer type, String tenantId, int priority);

  /**
   * Enters the job for activation the way brokers before 8.10 did: without priority, served after
   * the jobs of positive priority and before the jobs of non-positive priority. Setting the same
   * entry again is allowed.
   *
   * <p>Only the event appliers released before 8.10 call this, to reproduce the state of their
   * events on replay. A store that never replays such events doesn't need to support it.
   */
  void setLegacyActivatable(long key, DirectBuffer type, String tenantId);

  /**
   * Removes the job's activation entry for the type, tenant and priority, and the job's entry of
   * {@link #setLegacyActivatable}, if there are any.
   */
  void clearActivatable(long key, DirectBuffer type, String tenantId, int priority);

  /**
   * Visits the keys of the jobs entered for activation by workers of the type, of any of the
   * tenants, in activation order (see {@link #setActivatable} and {@link #setLegacyActivatable}),
   * until the visitor returns false.
   *
   * @param limitHint how many jobs the caller expects to accept at most, or {@link #NO_LIMIT_HINT};
   *     a store may use it to read ahead, but must keep visiting until the visitor returns false or
   *     no job is left, since the visitor may reject jobs
   */
  void visitActivatableJobs(
      DirectBuffer type, List<String> tenantIds, int limitHint, ActivatableJobKeyVisitor visitor);

  /** Enters the job's deadline, which must not be entered yet. */
  void setDeadline(long key, long deadline);

  /** Removes the job's deadline entry, if there is one with this deadline. */
  void clearDeadline(long key, long deadline);

  /** Removes the job's deadline entry, which must exist with this deadline. */
  void clearExistingDeadline(long key, long deadline);

  boolean hasDeadline(long key, long deadline);

  /**
   * Visits the keys of the jobs with a deadline before the timestamp, ordered by deadline and key,
   * starting at the given index, until the visitor returns false.
   *
   * @param startAt the index to start at (inclusive), or null to start at the first entry
   * @return the index of the entry at which the visitor returned false, or null if it didn't
   */
  @Nullable DeadlineIndex visitTimedOutJobs(
      long timestamp, @Nullable DeadlineIndex startAt, LongPredicate visitor);

  /**
   * Removes each deadline entry for which the predicate returns true. The predicate may read from
   * the store.
   */
  void removeDeadlinesIf(EntryPredicate predicate);

  /** Enters the job's backoff, which must not be entered yet. */
  void setBackoff(long key, long backoff);

  /** Removes the job's backoff entry, if there is one with this backoff. */
  void clearBackoff(long key, long backoff);

  /**
   * Visits the keys of the jobs with a backoff at or before the timestamp, ordered by backoff and
   * key, until the visitor returns false.
   *
   * @return the backoff of the first entry the visitor didn't accept, that is the entry at which it
   *     returned false or the first entry after the timestamp; -1 if there is none
   */
  long visitBackedOffJobs(long timestamp, LongPredicate visitor);

  /**
   * Removes each backoff entry for which the predicate returns true. The predicate may read from
   * the store.
   */
  void removeBackoffsIf(EntryPredicate predicate);

  /**
   * Enters the job in the lookup of the jobs of the process instance. Setting it again is allowed.
   */
  void addToProcessInstance(long processInstanceKey, long key);

  /** Removes the job from the lookup of the jobs of the process instance, if it is there. */
  void removeFromProcessInstance(long processInstanceKey, long key);

  /**
   * Visits the keys of the jobs entered for the process instance, in key order, until the visitor
   * returns false.
   *
   * @param startAtKey the job key to start at (inclusive), or a negative value to start at the
   *     first
   */
  void visitJobsOfProcessInstance(long processInstanceKey, long startAtKey, LongPredicate visitor);

  /** Visits the keys of activatable jobs: returns whether to continue with the next one. */
  @FunctionalInterface
  interface ActivatableJobKeyVisitor extends LongPredicate {

    /**
     * Called by a store that reads ahead, with the keys of the jobs it read, in the order it visits
     * them next. See {@link JobState.ActivatableJobVisitor#readAhead}.
     */
    default void readAhead(final long[] jobKeys) {}
  }

  /** Tests a deadline or backoff entry. */
  @FunctionalInterface
  interface EntryPredicate {

    /**
     * @param key the key of the job of the entry
     * @param value the deadline or backoff of the entry
     */
    boolean test(long key, long value);
  }
}
