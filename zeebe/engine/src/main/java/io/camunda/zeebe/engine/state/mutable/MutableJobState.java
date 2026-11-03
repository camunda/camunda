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
import java.util.function.LongPredicate;

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

  /**
   * Removes the {@code JOB_BACKOFF} entries that runtime could not have created: entries of missing
   * jobs, of jobs that are not {@code FAILED} or have no retries or retry backoff, and entries
   * whose time differs from the job's recurring time. Also removes the entries of jobs for which
   * {@code canBackOff} returns false.
   */
  void removeInvalidBackoffs(LongPredicate canBackOff);
}
