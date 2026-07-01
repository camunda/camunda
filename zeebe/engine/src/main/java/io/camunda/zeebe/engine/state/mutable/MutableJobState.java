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

  /**
   * Pre-prioritization create path. Inserts the job into the legacy {@code JOB_ACTIVATABLE} column
   * family, ignoring the {@code priority} field on the record. Used by {@code JobCreatedV2Applier}
   * so that records stamped with {@code recordVersion=2} (selected by the write side when {@link
   * io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability#JOB_PRIORITIZATION}
   * is inactive) produce identical state to a pre-PR broker.
   */
  void create(long key, JobRecord record);

  /**
   * Priority-aware create path. Inserts the job into {@code JOB_ACTIVATABLE_BY_PRIORITY} with the
   * record's priority as the sort key. Used by {@code JobCreatedV3Applier}, which is selected only
   * when ECV has activated {@link
   * io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability#JOB_PRIORITIZATION}.
   */
  void createWithPriorityActivation(long key, JobRecord record);

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

  void updateJobPriority(long jobKey, int newPriority);

  void migrate(long key, JobRecord record);

  void restoreBackoff();

  /**
   * Above-gate counterpart of {@link #activate(long, JobRecord)} for the first half of the two-step
   * activation flow: persists the worker-supplied record, transitions the job from {@code
   * State.ACTIVATABLE} to {@code State.RESERVED}, removes it from the activatable column families
   * so a subsequent activation batch can't re-claim it, and registers the activation deadline.
   * Invoked by {@code JobBatchReservedApplier}, which is itself gated under {@code
   * Capability.JOB_BATCH_RESERVATION_STATE}; below the gate this method is never called and the
   * single-step {@link #activate(long, JobRecord)} path remains in effect.
   */
  void reserve(long key, JobRecord record);

  /**
   * Above-gate counterpart of {@link #activate(long, JobRecord)} for the second half of the
   * two-step activation flow: flips the job's state from {@code State.RESERVED} to {@code
   * State.ACTIVATED}. All other invariants (activatable-CF removal, deadline tracking) were already
   * established by {@link #reserve(long, JobRecord)} in the same processor invocation, so this
   * method only mutates the state column. Invoked by {@code JobBatchActivatedV2Applier}, which is
   * gated under {@code Capability.JOB_BATCH_RESERVATION_STATE}.
   */
  void confirmReservation(long key);

  /**
   * Operator-initiated pause: transitions the job from {@code State.ACTIVATED} to {@code
   * State.PAUSED} and clears the timeout deadline so the {@code JobTimeOutProcessor} stops
   * re-driving it. Invoked by {@code JobPausedApplier} (gated under {@code
   * Capability.JOB_PAUSE_RESUME}); the corresponding {@code JobPauseProcessor} rejects the command
   * when the job is in any other state, so this method is only ever called from a known-good
   * predecessor state.
   */
  void pause(long key, JobRecord record);

  /**
   * Operator-initiated resume: transitions the job from {@code State.PAUSED} back to {@code
   * State.ACTIVATED} and re-registers the activation deadline carried on {@code record}. Mirror of
   * {@link #pause(long, JobRecord)}; invoked by {@code JobResumedApplier} (same gate) after {@code
   * JobResumeProcessor} has validated the predecessor state.
   */
  void resume(long key, JobRecord record);
}
