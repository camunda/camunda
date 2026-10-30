/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
<<<<<<< HEAD
=======
import java.util.function.LongPredicate;
import org.agrona.DirectBuffer;
>>>>>>> 5f74e87d (refactor: let the job backoff cleanup take a job filter)

public interface MutableJobState extends JobState {

  void create(long key, JobRecord record);

  void activate(long key, JobRecord record);

  void recurAfterBackoff(long key, JobRecord record);

  void timeout(long key, JobRecord record);

  void complete(long key, JobRecord record);

  void cancel(long key, JobRecord record);

  void disable(long key, JobRecord record);

  void throwError(long key, JobRecord updatedValue);

  void delete(long key, JobRecord record);

  void fail(long key, JobRecord updatedValue);

  void yield(long key, JobRecord updatedValue);

  void resolve(long key, JobRecord updatedValue);

  JobRecord updateJobRetries(long jobKey, int retries);

  void cleanupTimeoutsWithoutJobs();

  void cleanupBackoffsWithoutJobs();

  void updateJobDeadline(long jobKey, long newDeadline);

  void migrate(long key, JobRecord record);

<<<<<<< HEAD
  void restoreBackoff();
=======
  /**
   * Removes the {@code JOB_BACKOFF} entries of missing jobs and of jobs without retries or retry
   * backoff. Also removes the entries of jobs for which {@code canBackOff} returns false.
   */
  void removeInvalidBackoffs(LongPredicate canBackOff);

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
>>>>>>> 5f74e87d (refactor: let the job backoff cleanup take a job filter)
}
