/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.security.core.authz.TenantAccess;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.instance.JobStore.ActivatableJobKeyVisitor;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.util.EnsureUtil;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

/**
 * The job state, independent of where it is stored: decides on each transition which state a job
 * gets and which lookups (activation, deadline, backoff, process instance) it is entered in or
 * removed from. The {@link JobStore} persists it.
 */
public final class JobStateImpl implements MutableJobState {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final JobStore store;

  public JobStateImpl(final JobStore store) {
    this.store = store;
  }

  /**
   * @deprecated carries the pre-8.10 orchestration that enters the job in the legacy activation
   *     lookup. Reachable only from the released, unversioned/V1/V2 job appliers so that replaying
   *     their events reproduces the original state mutation. New appliers must call {@link
   *     #insertJobRecordActivatable(long, JobRecord)} and {@link
   *     #makeJobActivatableByPriority(DirectBuffer, long, String, int)} directly instead.
   */
  @Deprecated
  @Override
  public void create(final long key, final JobRecord record) {
    store.insertJob(key, record, State.ACTIVATABLE);
    makeJobActivatable(record.getTypeBuffer(), key, record.getTenantId());
  }

  @Override
  public void activate(final long key, final JobRecord record) {
    final DirectBuffer type = record.getTypeBuffer();
    final long deadline = record.getDeadline();

    validateParameters(type);
    EnsureUtil.ensureGreaterThan("deadline", deadline, 0);

    updateJobRecord(key, record);

    updateJobState(key, State.ACTIVATED);

    makeJobNotActivatable(key, record);

    addJobDeadline(key, deadline);
  }

  /**
   * @deprecated see {@link #create(long, JobRecord)}.
   */
  @Deprecated
  @Override
  public void recurAfterBackoff(final long key, final JobRecord record) {
    updateJob(key, record, State.ACTIVATABLE);
    removeJobBackoff(key, record.getRecurringTime());
  }

  /**
   * @deprecated see {@link #create(long, JobRecord)}.
   */
  @Deprecated
  @Override
  public void timeout(final long key, final JobRecord record) {
    final DirectBuffer type = record.getTypeBuffer();
    final long deadline = record.getDeadline();

    validateParameters(type);
    EnsureUtil.ensureGreaterThan("deadline", deadline, 0);

    updateJob(key, record, State.ACTIVATABLE);
  }

  @Override
  public void complete(final long key, final JobRecord record) {
    delete(key, record);
  }

  @Override
  public void cancel(final long key, final JobRecord record) {
    delete(key, record);
  }

  @Override
  public void disable(final long key, final JobRecord record) {
    updateJob(key, record, State.FAILED);
    makeJobNotActivatable(key, record);
  }

  @Override
  public void throwError(final long key, final JobRecord updatedValue) {
    updateJob(key, updatedValue, State.ERROR_THROWN);
    makeJobNotActivatable(key, updatedValue);
  }

  @Override
  public void delete(final long key, final JobRecord record) {
    store.deleteJob(key);

    makeJobNotActivatable(key, record);

    removeJobDeadline(key, record.getDeadline());
    removeJobBackoff(key, record.getRecurringTime());

    // a job that predates the process instance lookup and was never suspended isn't entered there
    store.removeFromProcessInstance(record.getProcessInstanceKey(), key);
  }

  /**
   * @deprecated see {@link #create(long, JobRecord)}.
   */
  @Deprecated
  @Override
  public void fail(final long key, final JobRecord updatedValue) {
    if (updatedValue.getRetries() > 0) {
      if (updatedValue.getRetryBackoff() > 0) {
        addJobBackoff(key, updatedValue.getRecurringTime());
        updateJob(key, updatedValue, State.FAILED);
        makeJobNotActivatable(key, updatedValue);
      } else {
        updateJob(key, updatedValue, State.ACTIVATABLE);
      }
    } else {
      updateJob(key, updatedValue, State.FAILED);
      makeJobNotActivatable(key, updatedValue);
    }
  }

  /**
   * @deprecated see {@link #create(long, JobRecord)}.
   */
  @Deprecated
  @Override
  public void yield(final long key, final JobRecord updatedValue) {
    updateJob(key, updatedValue, State.ACTIVATABLE);
  }

  @Override
  public void parkForSecretResolution(final long key, final JobRecord record) {
    final State state = getState(key);
    if (state != State.ACTIVATABLE && state != State.WAITING_FOR_SECRET_RESOLUTION) {
      // only a job that is up for activation can be withheld from it; parking a job a worker or a
      // failure already owns would take its state away from it
      return;
    }
    updateJobState(key, State.WAITING_FOR_SECRET_RESOLUTION);
    makeJobNotActivatable(key, record);
  }

  @Override
  public void makeActivatableAfterSecretResolution(final long key) {
    if (!isInState(key, State.WAITING_FOR_SECRET_RESOLUTION)) {
      // no-op unless waiting; re-inserting otherwise corrupts the activatable index
      return;
    }
    final JobRecord record = getJob(key);
    if (record == null) {
      // the state says the job is parked, so its record must exist; guarded anyway because an
      // exception in an event applier bans the process instance, or fails the partition on replay
      return;
    }
    updateJobState(key, State.ACTIVATABLE);
    makeJobActivatableByPriority(
        record.getTypeBuffer(), key, record.getTenantId(), record.getPriority());
  }

  /**
   * @deprecated see {@link #create(long, JobRecord)}.
   */
  @Deprecated
  @Override
  public void resolve(final long key, final JobRecord updatedValue) {
    updateJob(key, updatedValue, State.ACTIVATABLE);
  }

  @Override
  public JobRecord updateJobRetries(final long jobKey, final int retries) {
    final JobRecord job = getJob(jobKey);
    if (job != null) {
      job.setRetries(retries);
      updateJobRecord(jobKey, job);
    }
    return job;
  }

  @Override
  public void cleanupTimeoutsWithoutJobs() {
    store.removeDeadlinesIf(
        (jobKey, deadline) -> {
          final var job = store.getJob(jobKey);
          return job == null || job.getDeadline() != deadline;
        });
  }

  @Override
  public void cleanupBackoffsWithoutJobs() {
    store.removeBackoffsIf(
        (jobKey, backoff) -> {
          final var job = store.getJob(jobKey);
          if (job == null || job.getRecurringTime() != backoff) {
            LOG.debug("Deleting orphaned backoff {} of job with key {}", backoff, jobKey);
            return true;
          }
          return false;
        });
  }

  @Override
  public void updateJobDeadline(final long jobKey, final long newDeadline) {
    final JobRecord job = getJob(jobKey);

    if (job != null) {
      final long oldDeadline = job.getDeadline();

      store.clearExistingDeadline(jobKey, oldDeadline);

      job.setDeadline(newDeadline);
      updateJobRecord(jobKey, job);

      addJobDeadline(jobKey, newDeadline);
    }
  }

  @Override
  public void updateJobPriority(final long jobKey, final int newPriority) {
    final JobRecord job = getJob(jobKey);
    if (job == null) {
      return;
    }
    final int oldPriority = job.getPriority();
    job.setPriority(newPriority);

    if (getState(jobKey) == State.ACTIVATABLE) {
      makeJobNotActivatable(jobKey, job.getTypeBuffer(), job.getTenantId(), oldPriority);
      updateJobRecord(jobKey, job);
      makeJobActivatableByPriority(job.getTypeBuffer(), jobKey, job.getTenantId(), newPriority);
    } else {
      updateJobRecord(jobKey, job);
    }
  }

  @Override
  public void migrate(final long key, final JobRecord record) {
    updateJobRecord(key, record);
  }

  @Override
  public void removeInvalidBackoffs(final LongPredicate canBackOff) {
    store.removeBackoffsIf(
        (backedOffJobKey, backoff) -> {
          final var jobRecord = store.getJob(backedOffJobKey);
          return jobRecord == null
              || jobRecord.getRecurringTime() != backoff
              || jobRecord.getRetries() <= 0
              || jobRecord.getRetryBackoff() <= 0
              || getState(backedOffJobKey) != State.FAILED
              || !canBackOff.test(backedOffJobKey);
        });
  }

  @Override
  public void insertJobRecordActivatable(final long key, final JobRecord record) {
    store.insertJob(key, record, State.ACTIVATABLE);
    store.addToProcessInstance(record.getProcessInstanceKey(), key);
  }

  /** Updates the job record without updating variables */
  @Override
  public void updateJobRecord(final long key, final JobRecord updatedValue) {
    store.updateJob(key, updatedValue);
  }

  @Override
  public void updateJobState(final long key, final State newState) {
    store.updateState(key, newState);
    if (newState == State.SUSPENDED) {
      indexJobByProcessInstance(key);
    }
  }

  @Override
  public void removeJobDeadline(final long job, final long deadline) {
    if (deadline > 0) {
      store.clearDeadline(job, deadline);
    }
  }

  @Override
  public void makeJobActivatableByPriority(
      final DirectBuffer type, final long key, final String tenantId, final int priority) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);
    EnsureUtil.ensureNotNullOrEmpty("tenantId", tenantId);

    store.setActivatable(key, type, tenantId, priority);
  }

  @Override
  public void makeJobNotActivatable(final long key, final JobRecord record) {
    makeJobNotActivatable(key, record.getTypeBuffer(), record.getTenantId(), record.getPriority());
  }

  @Override
  public void addJobBackoff(final long job, final long backoff) {
    if (backoff > 0) {
      store.setBackoff(job, backoff);
    }
  }

  @Override
  public void removeJobBackoff(final long job, final long backoff) {
    if (backoff > 0) {
      store.clearBackoff(job, backoff);
    }
  }

  @Override
  public DeadlineIndex forEachTimedOutEntry(
      final long executionTimestamp,
      final DeadlineIndex startAt,
      final BiPredicate<Long, JobRecord> callback) {
    return store.visitTimedOutJobs(
        executionTimestamp, startAt, jobKey -> visitJob(jobKey, callback));
  }

  @Override
  public boolean exists(final long jobKey) {
    return store.exists(jobKey);
  }

  @Override
  public State getState(final long key) {
    final State state = store.getState(key);
    return state == null ? State.NOT_FOUND : state;
  }

  @Override
  public boolean isInState(final long key, final State state) {
    return getState(key) == state;
  }

  @Override
  public void forEachActivatableJobs(
      final DirectBuffer type,
      final List<String> tenantIds,
      final BiFunction<Long, JobRecord, Boolean> callback) {
    forEachActivatableJobs(type, tenantIds, JobStore.NO_LIMIT_HINT, callback::apply);
  }

  @Override
  public void forEachActivatableJobs(
      final DirectBuffer type,
      final List<String> tenantIds,
      final int limitHint,
      final ActivatableJobVisitor callback) {
    final BiPredicate<Long, JobRecord> jobVisitor = callback::apply;
    store.visitActivatableJobs(
        type,
        tenantIds,
        limitHint,
        new ActivatableJobKeyVisitor() {
          @Override
          public boolean test(final long jobKey) {
            return visitJob(jobKey, jobVisitor);
          }

          @Override
          public void readAhead(final long[] jobKeys) {
            callback.readAhead(jobKeys);
          }
        });
  }

  @Override
  public JobRecord getJob(final long key) {
    return store.getJob(key);
  }

  @Override
  public JobRecord getJob(final long key, final TenantAccess authorizedTenantIds) {
    final JobRecord jobRecord = getJob(key);
    if (jobRecord != null && authorizedTenantIds.isAuthorizedForTenantId(jobRecord.getTenantId())) {
      return jobRecord;
    }
    return null;
  }

  @Override
  public boolean jobDeadlineExists(final long jobKey, final long deadline) {
    return store.hasDeadline(jobKey, deadline);
  }

  @Override
  public long findBackedOffJobs(final long timestamp, final BiPredicate<Long, JobRecord> callback) {
    return store.visitBackedOffJobs(timestamp, jobKey -> visitJob(jobKey, callback));
  }

  @Override
  public void forEachJobsByProcessInstance(
      final long processInstanceKey, final long startAtJobKey, final LongPredicate visitor) {
    store.visitJobsOfProcessInstance(processInstanceKey, startAtJobKey, visitor);
  }

  /**
   * Backfills the process instance lookup for a job that reaches {@link State#SUSPENDED}. A
   * pre-8.10 job created via the deprecated {@link #create} path was never entered at creation;
   * suspension is the point every such job passes through, so enter it here. An 8.10+ job is
   * already entered by {@link #insertJobRecordActivatable}, and entering it again is harmless.
   * Reads the persisted record for the {@code processInstanceKey} rather than trusting a
   * caller-supplied one, so the lookup maintenance stays wholly inside this class.
   */
  private void indexJobByProcessInstance(final long key) {
    final JobRecord job = getJob(key);
    if (job == null) {
      return;
    }
    store.addToProcessInstance(job.getProcessInstanceKey(), key);
  }

  private void updateJob(final long key, final JobRecord updatedValue, final State newState) {
    final DirectBuffer type = updatedValue.getTypeBuffer();

    validateParameters(type);

    updateJobRecord(key, updatedValue);

    updateJobState(key, newState);

    if (newState == State.ACTIVATABLE) {
      makeJobActivatable(type, key, updatedValue.getTenantId());
    }

    if (newState != State.ACTIVATED) {
      // This only works because none of the events actually remove the deadline from the job
      // record.
      // If, say on job failure, the deadline is removed or reset to 0, then we would need to look
      // at the current state of the job to determine what deadline to remove.
      removeJobDeadline(key, updatedValue.getDeadline());
    }
  }

  private void validateParameters(final DirectBuffer type) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);
  }

  private boolean visitJob(final long jobKey, final BiPredicate<Long, JobRecord> callback) {
    final JobRecord job = getJob(jobKey);
    if (job == null) {
      LOG.warn("Expected to find job with key {}, but no job found", jobKey);
      return true; // we want to continue with the iteration
    }
    return callback.test(jobKey, job);
  }

  /**
   * @deprecated restored pre-8.10 insert semantics: enters the job only in the legacy activation
   *     lookup, with no notion of priority. Reachable only from the deprecated,
   *     released-before-8.10 orchestration methods ({@link #create(long, JobRecord)}, {@link
   *     #fail(long, JobRecord)}, etc.) so that replaying their events reproduces the original state
   *     mutation. New appliers must call {@link #makeJobActivatableByPriority(DirectBuffer, long,
   *     String, int)} instead.
   */
  @Deprecated
  private void makeJobActivatable(final DirectBuffer type, final long key, final String tenantId) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);
    EnsureUtil.ensureNotNullOrEmpty("tenantId", tenantId);

    store.setLegacyActivatable(key, type, tenantId);
  }

  private void makeJobNotActivatable(
      final long key, final DirectBuffer type, final String tenantId, final int priority) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);
    EnsureUtil.ensureNotNullOrEmpty("tenantId", tenantId);

    store.clearActivatable(key, type, tenantId, priority);
  }

  private void addJobDeadline(final long job, final long deadline) {
    if (deadline > 0) {
      store.setDeadline(job, deadline);
    }
  }
}
