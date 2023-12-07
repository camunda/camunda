/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.util.EnsureUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public final class DbJobState implements JobState, MutableJobState {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  // key => job record value
  // we need two separate wrapper to not interfere with get and put
  // see https://github.com/zeebe-io/zeebe/issues/1914
  private final JobRecordValue jobRecordToRead = new JobRecordValue();
  private final JobRecordValue jobRecordToWrite = new JobRecordValue();

  private final DbLong jobKey;
  private final DbForeignKey<DbLong> fkJob;
  private final ColumnFamily<DbLong, JobRecordValue> jobsColumnFamily;

  // key => job state
  private final JobStateValue jobState = new JobStateValue();
  private final ColumnFamily<DbForeignKey<DbLong>, JobStateValue> statesJobColumnFamily;

  // type => [key]
  private final DbString jobTypeKey;
  private final DbCompositeKey<DbString, DbForeignKey<DbLong>> typeJobKey;
  private final ColumnFamily<DbCompositeKey<DbString, DbForeignKey<DbLong>>, DbNil>
      activatableColumnFamily;

  // timeout => key
  private final DbLong deadlineKey;
  private final DbCompositeKey<DbLong, DbForeignKey<DbLong>> deadlineJobKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbForeignKey<DbLong>>, DbNil>
      deadlinesColumnFamily;

  private final DbLong backoffKey;
  private final DbCompositeKey<DbLong, DbForeignKey<DbLong>> backoffJobKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbForeignKey<DbLong>>, DbNil>
      backoffColumnFamily;
  private long nextBackOffDueDate;

  private final JobMetrics metrics;

  private Consumer<String> onJobsAvailableCallback;

  public DbJobState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final int partitionId) {
    jobKey = new DbLong();
    fkJob = new DbForeignKey<>(jobKey, ZbColumnFamilies.JOBS);
    jobsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOBS, transactionContext, jobKey, jobRecordToRead);

    statesJobColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_STATES, transactionContext, fkJob, jobState);

    jobTypeKey = new DbString();
    typeJobKey = new DbCompositeKey<>(jobTypeKey, fkJob);
    activatableColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_ACTIVATABLE, transactionContext, typeJobKey, DbNil.INSTANCE);

    deadlineKey = new DbLong();
    deadlineJobKey = new DbCompositeKey<>(deadlineKey, fkJob);
    deadlinesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_DEADLINES, transactionContext, deadlineJobKey, DbNil.INSTANCE);

    backoffKey = new DbLong();
    backoffJobKey = new DbCompositeKey<>(backoffKey, fkJob);
    backoffColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_BACKOFF, transactionContext, backoffJobKey, DbNil.INSTANCE);

    metrics = new JobMetrics(partitionId);
  }

  @Override
  public void create(final long key, final JobRecord record) {
    final DirectBuffer type = record.getTypeBuffer();
    createJob(key, record, type);
  }

  @Override
  public void activate(final long key, final JobRecord record) {
    final DirectBuffer type = record.getTypeBuffer();
    final long deadline = record.getDeadline();

    validateParameters(type);
    EnsureUtil.ensureGreaterThan("deadline", deadline, 0);

    updateJobRecord(key, record);

    updateJobState(State.ACTIVATED);

    makeJobNotActivatable(type);

    addJobDeadline(key, deadline);
  }

  @Override
  public void recurAfterBackoff(final long key, final JobRecord record) {
    updateJob(key, record, State.ACTIVATABLE);
    removeJobBackoff(key, record.getRecurringTime());
  }

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
    makeJobNotActivatable(record.getTypeBuffer());
  }

  @Override
  public void throwError(final long key, final JobRecord updatedValue) {
    updateJob(key, updatedValue, State.ERROR_THROWN);
    makeJobNotActivatable(updatedValue.getTypeBuffer());
  }

  @Override
  public void delete(final long key, final JobRecord record) {
    final DirectBuffer type = record.getTypeBuffer();

    jobKey.wrapLong(key);
    jobsColumnFamily.deleteExisting(jobKey);

    statesJobColumnFamily.deleteExisting(fkJob);

    makeJobNotActivatable(type);

    removeJobDeadline(key, record.getDeadline());
    removeJobBackoff(key, record.getRecurringTime());
  }

  @Override
  public void fail(final long key, final JobRecord updatedValue) {
    if (updatedValue.getRetries() > 0) {
      if (updatedValue.getRetryBackoff() > 0) {
        addJobBackoff(key, updatedValue.getRecurringTime());
        updateJob(key, updatedValue, State.FAILED);
      } else {
        updateJob(key, updatedValue, State.ACTIVATABLE);
      }
    } else {
      updateJob(key, updatedValue, State.FAILED);
      makeJobNotActivatable(updatedValue.getTypeBuffer());
    }
  }

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
    deadlinesColumnFamily.whileTrue(
        (key, value) -> {
          final var jobKey = key.second().inner();
          final var deadline = key.first().getValue();
          final var job = jobsColumnFamily.get(jobKey);
          if (job == null || job.getRecord().getDeadline() != deadline) {
            deadlinesColumnFamily.deleteExisting(key);
          }
          return true;
        });
  }

  @Override
  public void cleanupBackoffsWithoutJobs() {
    backoffColumnFamily.whileTrue(
        (key, value) -> {
          final var jobKey = key.second().inner();
          final var backoff = key.first().getValue();
          final var job = jobsColumnFamily.get(jobKey);
          if (job == null || job.getRecord().getRecurringTime() != backoff) {
            LOG.debug("Deleting orphaned job with key {}", key);
            backoffColumnFamily.deleteExisting(key);
          }
          return true;
        });
  }

  @Override
  public void restoreBackoff() {
    final var failedKeys = getFailedJobKeys();

    failedKeys.removeAll(getBackoffJobKeys());
    failedKeys.forEach(
        key -> {
          jobKey.wrapLong(key);
          final var jobRecord = jobsColumnFamily.get(jobKey);
          if (jobRecord != null && jobRecord.getRecord().getRecurringTime() > -1) {
            addJobBackoff(key, jobRecord.getRecord().getRecurringTime());
          }
        });
  }

  private void createJob(final long key, final JobRecord record, final DirectBuffer type) {
    createJobRecord(key, record);
    initializeJobState();
    makeJobActivatable(type, key);
  }

  private void updateJob(final long key, final JobRecord updatedValue, final State newState) {
    final DirectBuffer type = updatedValue.getTypeBuffer();

    validateParameters(type);

    updateJobRecord(key, updatedValue);

    updateJobState(newState);

    if (newState == State.ACTIVATABLE) {
      makeJobActivatable(type, key);
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

  @Override
  public void forEachTimedOutEntry(
      final long upperBound, final BiPredicate<Long, JobRecord> callback) {
    deadlinesColumnFamily.whileTrue(
        (key, value) -> {
          final long deadline = key.first().getValue();
          final boolean isDue = deadline < upperBound;
          if (isDue) {
            final long jobKey1 = key.second().inner().getValue();
            return visitJob(jobKey1, callback);
          }
          return false;
        });
  }

  @Override
  public boolean exists(final long jobKey) {
    this.jobKey.wrapLong(jobKey);
    return jobsColumnFamily.exists(this.jobKey);
  }

  @Override
  public State getState(final long key) {
    jobKey.wrapLong(key);

    final JobStateValue storedState = statesJobColumnFamily.get(fkJob);

    if (storedState == null) {
      return State.NOT_FOUND;
    }

    return storedState.getState();
  }

  @Override
  public boolean isInState(final long key, final State state) {
    return getState(key) == state;
  }

  @Override
  public void forEachActivatableJobs(
      final DirectBuffer type, final BiFunction<Long, JobRecord, Boolean> callback) {
    jobTypeKey.wrapBuffer(type);

    activatableColumnFamily.whileEqualPrefix(
        jobTypeKey,
        ((compositeKey, zbNil) -> {
          final long jobKey = compositeKey.second().inner().getValue();
          return visitJob(jobKey, callback::apply);
        }));
  }

  @Override
  public JobRecord getJob(final long key) {
    jobKey.wrapLong(key);
    final JobRecordValue jobState = jobsColumnFamily.get(jobKey);
    return jobState == null ? null : jobState.getRecord();
  }

  @Override
  public void setJobsAvailableCallback(final Consumer<String> onJobsAvailableCallback) {
    this.onJobsAvailableCallback = onJobsAvailableCallback;
  }

  @Override
  public long findBackedOffJobs(final long timestamp, final BiPredicate<Long, JobRecord> callback) {
    nextBackOffDueDate = -1L;
    backoffColumnFamily.whileTrue(
        (key, value) -> {
          final long deadline = key.first().getValue();
          boolean consumed = false;
          if (deadline <= timestamp) {
            final long jobKey = key.second().inner().getValue();
            consumed = visitJob(jobKey, callback);
          }
          if (!consumed) {
            nextBackOffDueDate = deadline;
          }
          return consumed;
        });
    return nextBackOffDueDate;
  }

  boolean visitJob(final long jobKey, final BiPredicate<Long, JobRecord> callback) {
    final JobRecord job = getJob(jobKey);
    if (job == null) {
      LOG.error("Expected to find job with key {}, but no job found", jobKey);
      return true; // we want to continue with the iteration
    }
    return callback.test(jobKey, job);
  }

  private void notifyJobAvailable(final DirectBuffer jobType) {
    if (onJobsAvailableCallback != null) {
      onJobsAvailableCallback.accept(BufferUtil.bufferAsString(jobType));
    }
  }

  private void createJobRecord(final long key, final JobRecord record) {
    jobKey.wrapLong(key);
    // do not persist variables in job state
    jobRecordToWrite.setRecordWithoutVariables(record);
    jobsColumnFamily.insert(jobKey, jobRecordToWrite);
  }

  /** Updates the job record without updating variables */
  private void updateJobRecord(final long key, final JobRecord updatedValue) {
    jobKey.wrapLong(key);
    // do not persist variables in job state
    jobRecordToWrite.setRecordWithoutVariables(updatedValue);
    jobsColumnFamily.update(jobKey, jobRecordToWrite);
  }

  private void initializeJobState() {
    jobState.setState(State.ACTIVATABLE);
    statesJobColumnFamily.insert(fkJob, jobState);
  }

  private void updateJobState(final State newState) {
    jobState.setState(newState);
    statesJobColumnFamily.update(fkJob, jobState);
  }

  private void makeJobActivatable(final DirectBuffer type, final long key) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);

    jobTypeKey.wrapBuffer(type);

    jobKey.wrapLong(key);
    // Need to upsert here because jobs can be marked as failed (and thus made activatable)
    // without activating them first
    activatableColumnFamily.upsert(typeJobKey, DbNil.INSTANCE);

    // always notify
    notifyJobAvailable(type);
  }

  private void makeJobNotActivatable(final DirectBuffer type) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);

    jobTypeKey.wrapBuffer(type);
    activatableColumnFamily.deleteIfExists(typeJobKey);
  }

  private void addJobDeadline(final long job, final long deadline) {
    if (deadline > 0) {
      jobKey.wrapLong(job);
      deadlineKey.wrapLong(deadline);
      deadlinesColumnFamily.insert(deadlineJobKey, DbNil.INSTANCE);
    }
  }

  private void removeJobDeadline(final long job, final long deadline) {
    if (deadline > 0) {
      jobKey.wrapLong(job);
      deadlineKey.wrapLong(deadline);
      deadlinesColumnFamily.deleteIfExists(deadlineJobKey);
    }
  }

  private void addJobBackoff(final long job, final long backoff) {
    if (backoff > 0) {
      jobKey.wrapLong(job);
      backoffKey.wrapLong(backoff);
      backoffColumnFamily.insert(backoffJobKey, DbNil.INSTANCE);
    }
  }

  private void removeJobBackoff(final long job, final long backoff) {
    if (backoff > 0) {
      jobKey.wrapLong(job);
      backoffKey.wrapLong(backoff);
      backoffColumnFamily.deleteIfExists(backoffJobKey);
    }
  }

  private Set<Long> getFailedJobKeys() {
    final Set<Long> failedJobKeys = new HashSet<>();
    statesJobColumnFamily.forEach(
        (key, value) -> {
          if ((State.FAILED).equals(value.getState())) {
            failedJobKeys.add(key.inner().getValue());
          }
        });
    return failedJobKeys;
  }

  private Set<Long> getBackoffJobKeys() {
    final Set<Long> backoffJobKeys = new HashSet<>();
    backoffColumnFamily.forEach(
        (key, value) -> backoffJobKeys.add(key.second().inner().getValue()));
    return backoffJobKeys;
  }
}
