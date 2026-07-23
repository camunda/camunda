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
   * reactivated by more than one resolved reference of the same activation and may be gone by then.
   * A job that carries a secret resolution incident is still waiting, so keeping it parked until
   * the incident is resolved is up to the caller.
   */
  void makeActivatableAfterSecretResolution(long key);

  void resolve(long key, JobRecord updatedValue);

  JobRecord updateJobRetries(long jobKey, int retries);

  void cleanupTimeoutsWithoutJobs();

  void cleanupBackoffsWithoutJobs();

  void updateJobDeadline(long jobKey, long newDeadline);

  void updateJobPriority(long jobKey, int newPriority);

  void migrate(long key, JobRecord record);

  void restoreBackoff();
}
