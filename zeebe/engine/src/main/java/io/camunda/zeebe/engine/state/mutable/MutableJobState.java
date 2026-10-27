/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import org.agrona.DirectBuffer;

public interface MutableJobState extends JobState {

  /**
   * @deprecated inserts the activatable job into the legacy {@code JOB_ACTIVATABLE} column family.
   *     Reachable only from the released, unversioned/V1/V2 job appliers so that replaying their
   *     events reproduces the original state mutation. New appliers must use {@link
   *     #insertJobRecordActivatable(long, JobRecord)} and {@link
   *     #makeJobActivatableByPriority(DirectBuffer, long, String, int)} directly.
   */
  @Deprecated
  void create(long key, JobRecord record);

  void activate(long key, JobRecord record);

  /**
   * @deprecated see {@link #create(long, JobRecord)}.
   */
  @Deprecated
  void recurAfterBackoff(long key, JobRecord record);

  /**
   * @deprecated see {@link #create(long, JobRecord)}.
   */
  @Deprecated
  void timeout(long key, JobRecord record);

  void complete(long key, JobRecord record);

  void cancel(long key, JobRecord record);

  void disable(long key, JobRecord record);

  void throwError(long key, JobRecord updatedValue);

  void delete(long key, JobRecord record);

  /**
   * @deprecated see {@link #create(long, JobRecord)}.
   */
  @Deprecated
  void fail(long key, JobRecord updatedValue);

  /**
   * @deprecated see {@link #create(long, JobRecord)}.
   */
  @Deprecated
  void yield(long key, JobRecord updatedValue);

  /**
   * Parks a job in {@link State#WAITING_FOR_SECRET_RESOLUTION} and removes it from the activatable
   * index, so it is handed out to no worker while its secret references are resolved. Does nothing
   * unless the job is up for activation; parking a job that already waits on another reference of
   * the same activation is idempotent.
   */
  void parkForSecretResolution(long key, JobRecord record);

  /**
   * Makes a job activatable after its pending secret references have been resolved. Does nothing
   * unless the job is in {@link State#WAITING_FOR_SECRET_RESOLUTION}, because a job can be
   * reactivated by more than one resolved reference of the same activation, or may have left that
   * state for another reason (for example suspension) by then. A job that carries a secret
   * resolution incident is still waiting, so keeping it parked until the incident is resolved is up
   * to the caller.
   */
  void makeActivatableAfterSecretResolution(long key);

  /**
   * @deprecated see {@link #create(long, JobRecord)}.
   */
  @Deprecated
  void resolve(long key, JobRecord updatedValue);

  JobRecord updateJobRetries(long jobKey, int retries);

  void cleanupTimeoutsWithoutJobs();

  void cleanupBackoffsWithoutJobs();

  void updateJobDeadline(long jobKey, long newDeadline);

  void updateJobPriority(long jobKey, int newPriority);

  void migrate(long key, JobRecord record);

  void restoreBackoff();

  /**
   * Inserts a new job record and marks it {@code ACTIVATABLE}. Callers must follow up with {@link
   * #makeJobActivatableByPriority(DirectBuffer, long, String, int)}.
   */
  void insertJobRecordActivatable(long key, JobRecord record);

  /** Updates the {@code JOB} column family */
  void updateJobRecord(long key, JobRecord updatedValue);

  /** Updates the {@code JOB_STATE} column family. */
  void updateJobState(long key, State newState);

  /** Removes from the {@code JOB_DEADLINES} column family. */
  void removeJobDeadline(long key, long deadline);

  /** Inserts into the {@code JOB_ACTIVATABLE_BY_PRIORITY} column family */
  void makeJobActivatableByPriority(DirectBuffer type, long key, String tenantId, int priority);

  /**
   * Removes from both legacy {@code JOB_ACTIVATABLE} and {@code JOB_ACTIVATABLE_BY_PRIORITY} column
   * families.
   */
  void makeJobNotActivatable(long key, JobRecord record);

  /** Inserts into the {@code JOB_BACKOFF} column family. */
  void addJobBackoff(long job, long backoff);

  /** Removes from the {@code JOB_BACKOFF} column family. */
  void removeJobBackoff(long job, long backoff);
}
