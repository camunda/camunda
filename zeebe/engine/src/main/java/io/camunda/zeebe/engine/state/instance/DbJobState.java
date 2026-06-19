/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.util.EnsureUtil;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongHashSet;
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

  // [[type, key], tenant_id] => nil
  private final DbString jobTypeKey;
  private final DbString tenantIdKey;
  private final DbCompositeKey<DbString, DbForeignKey<DbLong>> typeJobKey;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbForeignKey<DbLong>>>
      tenantAwareTypeJobKey;
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbForeignKey<DbLong>>>, DbNil>
      activatableColumnFamily;

  // invertedPriority = Integer.MAX_VALUE - priority so higher-priority entries sort first
  // under RocksDB's unsigned byte comparator. For negative priorities the subtraction
  // overflows (e.g. priority=-1 → invertedPriority=Integer.MIN_VALUE, stored as 0x80000000);
  // this is intentional: 0x80000000 sorts after 0x7FFFFFFF (priority=0) in unsigned order,
  // so negative-priority jobs correctly sort last.
  private final DbInt invertedPriorityKey;
  private final DbCompositeKey<DbInt, DbForeignKey<DbLong>> invertedPriorityJobKey;
  private final DbCompositeKey<DbString, DbCompositeKey<DbInt, DbForeignKey<DbLong>>>
      typePriorityJobKey;
  private final DbTenantAwareKey<
          DbCompositeKey<DbString, DbCompositeKey<DbInt, DbForeignKey<DbLong>>>>
      tenantAwarePriorityKey;

  // CF JOB_ACTIVATABLE_BY_PRIORITY: key = [type | invertedPriority | jobKey | tenantId]
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbCompositeKey<DbInt, DbForeignKey<DbLong>>>>,
          DbNil>
      priorityActivatableColumnFamily;

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

  public DbJobState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    jobKey = new DbLong();
    fkJob = new DbForeignKey<>(jobKey, ZbColumnFamilies.JOBS);
    jobsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOBS, transactionContext, jobKey, jobRecordToRead);

    statesJobColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_STATES, transactionContext, fkJob, jobState);

    jobTypeKey = new DbString();
    tenantIdKey = new DbString();
    typeJobKey = new DbCompositeKey<>(jobTypeKey, fkJob);
    tenantAwareTypeJobKey = new DbTenantAwareKey<>(tenantIdKey, typeJobKey, PlacementType.SUFFIX);
    activatableColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_ACTIVATABLE,
            transactionContext,
            tenantAwareTypeJobKey,
            DbNil.INSTANCE);

    invertedPriorityKey = new DbInt();
    invertedPriorityJobKey = new DbCompositeKey<>(invertedPriorityKey, fkJob);
    typePriorityJobKey = new DbCompositeKey<>(jobTypeKey, invertedPriorityJobKey);
    tenantAwarePriorityKey =
        new DbTenantAwareKey<>(tenantIdKey, typePriorityJobKey, PlacementType.SUFFIX);
    priorityActivatableColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_ACTIVATABLE_BY_PRIORITY,
            transactionContext,
            tenantAwarePriorityKey,
            DbNil.INSTANCE);

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
  }

  @Override
  public void create(final long key, final JobRecord record) {
    final DirectBuffer type = record.getTypeBuffer();
    createJobLegacy(key, record, type);
  }

  @Override
  public void createWithPriorityActivation(final long key, final JobRecord record) {
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

    makeJobNotActivatable(record);

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
    makeJobNotActivatable(record);
  }

  @Override
  public void throwError(final long key, final JobRecord updatedValue) {
    updateJob(key, updatedValue, State.ERROR_THROWN);
    makeJobNotActivatable(updatedValue);
  }

  @Override
  public void delete(final long key, final JobRecord record) {
    jobKey.wrapLong(key);
    jobsColumnFamily.deleteExisting(jobKey);

    statesJobColumnFamily.deleteExisting(fkJob);

    makeJobNotActivatable(record);

    removeJobDeadline(key, record.getDeadline());
    removeJobBackoff(key, record.getRecurringTime());
  }

  @Override
  public void fail(final long key, final JobRecord updatedValue) {
    if (updatedValue.getRetries() > 0) {
      if (updatedValue.getRetryBackoff() > 0) {
        addJobBackoff(key, updatedValue.getRecurringTime());
        updateJob(key, updatedValue, State.FAILED);
        makeJobNotActivatable(updatedValue);
      } else {
        updateJob(key, updatedValue, State.ACTIVATABLE);
      }
    } else {
      updateJob(key, updatedValue, State.FAILED);
      makeJobNotActivatable(updatedValue);
    }
  }

  @Override
  public void yield(final long key, final JobRecord updatedValue) {
    updateJob(key, updatedValue, State.ACTIVATABLE);
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
        key -> {
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
        key -> {
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
  public void updateJobDeadline(final long jobKey, final long newDeadline) {
    this.jobKey.wrapLong(jobKey);
    final JobRecord job = getJob(jobKey);

    if (job != null) {
      final long oldDeadline = job.getDeadline();

      deadlineKey.wrapLong(oldDeadline);
      deadlinesColumnFamily.deleteExisting(deadlineJobKey);

      job.setDeadline(newDeadline);
      updateJobRecord(jobKey, job);

      addJobDeadline(jobKey, newDeadline);
    }
  }

  @Override
  public void updateJobPriority(final long jobKey, final int newPriority) {
    this.jobKey.wrapLong(jobKey);
    final JobRecord job = getJob(jobKey);
    if (job == null) {
      return;
    }
    final int oldPriority = job.getPriority();
    job.setPriority(newPriority);

    if (getState(jobKey) == State.ACTIVATABLE) {
      makeJobNotActivatable(job.getTypeBuffer(), job.getTenantId(), oldPriority);
      updateJobRecord(jobKey, job);
      makeJobActivatable(job.getTypeBuffer(), jobKey, job.getTenantId(), newPriority);
    } else {
      updateJobRecord(jobKey, job);
    }
  }

  @Override
  public void migrate(final long key, final JobRecord record) {
    updateJobRecord(key, record);
  }

  @Override
  public void restoreBackoff() {
    final var jobsWithBackoff = new LongHashSet();
    backoffColumnFamily.forEachKey(
        (key) -> {
          final var jobRecord = jobsColumnFamily.get(jobKey);
          if (jobRecord == null
              || jobRecord.getRecord().getRetries() <= 0
              || jobRecord.getRecord().getRetryBackoff() <= 0) {
            backoffColumnFamily.deleteExisting(key);
          } else {
            jobsWithBackoff.add(jobKey.getValue());
          }
        });

    statesJobColumnFamily.forEach(
        value -> {
          if (!State.FAILED.equals(value.getState())) {
            return;
          }
          if (jobsWithBackoff.contains(jobKey.getValue())) {
            return;
          }
          final var jobRecord = jobsColumnFamily.get(jobKey);
          final var backoff = jobRecord.getRecord().getRecurringTime();
          final var retries = jobRecord.getRecord().getRetries();
          if (backoff > 0 && retries > 0) {
            backoffKey.wrapLong(backoff);
            backoffColumnFamily.insert(backoffJobKey, DbNil.INSTANCE);
          }
        });
  }

  @Override
  public void reserve(final long key, final JobRecord record) {
    // First half of the two-step activation flow (above-gate). Mirrors activate() but stops at
    // State.RESERVED instead of advancing to ACTIVATED — the second-half applier flips that.
    final DirectBuffer type = record.getTypeBuffer();
    final long deadline = record.getDeadline();

    validateParameters(type);
    EnsureUtil.ensureGreaterThan("deadline", deadline, 0);

    updateJobRecord(key, record);
    updateJobState(State.RESERVED);
    makeJobNotActivatable(record);
    addJobDeadline(key, deadline);
  }

  @Override
  public void confirmReservation(final long key) {
    // Second half: the record/CF/deadline work was already done by reserve(); only the state
    // column flips. updateJobState reads from the per-call jobKey wrapping, so set it first.
    jobKey.wrapLong(key);
    updateJobState(State.ACTIVATED);
  }

  private void createJob(final long key, final JobRecord record, final DirectBuffer type) {
    createJobRecord(key, record);
    initializeJobState();
    makeJobActivatable(type, key, record.getTenantId(), record.getPriority());
  }

  /**
   * Pre-prioritization create. Writes the job to {@code JOB_ACTIVATABLE} (legacy CF) and does not
   * touch {@code JOB_ACTIVATABLE_BY_PRIORITY}. Used by the v=2 applier so a low-ECV cluster's state
   * is identical to a pre-PR broker's.
   */
  private void createJobLegacy(final long key, final JobRecord record, final DirectBuffer type) {
    createJobRecord(key, record);
    initializeJobState();
    makeJobActivatableLegacy(type, key, record.getTenantId());
  }

  private void makeJobActivatableLegacy(
      final DirectBuffer type, final long key, final String tenantId) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);
    EnsureUtil.ensureNotNullOrEmpty("tenantId", tenantId);
    jobTypeKey.wrapBuffer(type);
    jobKey.wrapLong(key);
    tenantIdKey.wrapString(tenantId);
    activatableColumnFamily.upsert(tenantAwareTypeJobKey, DbNil.INSTANCE);
  }

  private void updateJob(final long key, final JobRecord updatedValue, final State newState) {
    final DirectBuffer type = updatedValue.getTypeBuffer();

    validateParameters(type);

    updateJobRecord(key, updatedValue);

    updateJobState(newState);

    if (newState == State.ACTIVATABLE) {
      makeJobActivatable(type, key, updatedValue.getTenantId(), updatedValue.getPriority());
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
  public DeadlineIndex forEachTimedOutEntry(
      final long executionTimestamp,
      final DeadlineIndex startAt,
      final BiPredicate<Long, JobRecord> callback) {

    final DbCompositeKey<DbLong, DbForeignKey<DbLong>> startAtKey;
    if (startAt != null) {
      deadlineKey.wrapLong(startAt.deadline());
      jobKey.wrapLong(startAt.key());
      startAtKey = deadlineJobKey;
    } else {
      startAtKey = null;
    }

    final var lastVisitedIndex = new AtomicReference<DeadlineIndex>();
    deadlinesColumnFamily.whileTrue(
        startAtKey,
        key -> {
          final var deadline = key.first().getValue();
          final var isDue = deadline < executionTimestamp;
          if (!isDue) {
            return false;
          }
          final var jobKey = key.second().inner().getValue();
          if (!visitJob(jobKey, callback)) {
            lastVisitedIndex.set(
                new DeadlineIndex(key.first().getValue(), key.second().inner().getValue()));
            return false;
          }
          return true;
        });

    return lastVisitedIndex.get();
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
      final DirectBuffer type,
      final List<String> tenantIds,
      final BiFunction<Long, JobRecord, Boolean> callback) {
    jobTypeKey.wrapBuffer(type);

    // Three-phase sequential read ensures correct activation order across the 8.10 upgrade
    // boundary:
    //   Phase 1: new jobs with priority > 0, highest first
    //   Phase 2: legacy pre-8.10 jobs from JOB_ACTIVATABLE (implicit priority = 0, lower job
    // keys)
    //   Phase 3: new jobs with priority <= 0, highest first
    // Each phase returns true if the batch still wants more jobs. Subsequent phases are skipped
    // when the batch is full to avoid redundant state reads.
    if (visitHighPriorityJobs(tenantIds, callback)
        && visitLegacyActivatableJobs(tenantIds, callback)) {
      visitNonPositivePriorityJobs(tenantIds, callback);
    }
  }

  @Override
  public JobRecord getJob(final long key) {
    jobKey.wrapLong(key);
    final JobRecordValue jobState = jobsColumnFamily.get(jobKey);
    return jobState == null ? null : jobState.getRecord();
  }

  @Override
  public JobRecord getJob(final long key, final AuthorizedTenants authorizedTenantIds) {
    final JobRecord jobRecord = getJob(key);
    if (jobRecord != null && authorizedTenantIds.isAuthorizedForTenantId(jobRecord.getTenantId())) {
      return jobRecord;
    }
    return null;
  }

  @Override
  public boolean jobDeadlineExists(final long jobKey, final long deadline) {
    this.jobKey.wrapLong(jobKey);
    deadlineKey.wrapLong(deadline);
    return deadlinesColumnFamily.exists(deadlineJobKey);
  }

  @Override
  public long findBackedOffJobs(final long timestamp, final BiPredicate<Long, JobRecord> callback) {
    nextBackOffDueDate = -1L;
    backoffColumnFamily.whileTrue(
        key -> {
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

  /**
   * Phase 1 — yields all priority > 0 jobs from {@code JOB_ACTIVATABLE_BY_PRIORITY} in descending
   * priority order. Stops at the first entry where the actual priority drops to 0 or below.
   *
   * <p>The inverted-priority key scheme encodes {@code invertedPriority = Integer.MAX_VALUE -
   * priority}, so RocksDB's ascending forward scan returns higher-priority entries first. The
   * boundary is detected by recovering {@code actualPriority = Integer.MAX_VALUE -
   * invertedPriority} using two's-complement arithmetic — this is intentional and correct for
   * negative priorities; see the field comment on {@code invertedPriorityKey}.
   *
   * @return {@code true} if the batch still wants more jobs; {@code false} if the batch is full
   */
  private boolean visitHighPriorityJobs(
      final List<String> tenantIds, final BiFunction<Long, JobRecord, Boolean> callback) {
    final var batchNotFull = new boolean[] {true};
    priorityActivatableColumnFamily.whileEqualPrefix(
        jobTypeKey,
        entry -> {
          final var invertedPriorityAndJob = entry.wrappedKey().second();
          final int priority = Integer.MAX_VALUE - invertedPriorityAndJob.first().getValue();
          if (priority <= 0) {
            return false; // reached priority=0 boundary; priority<=0 jobs served after legacy drain
          }
          if (!tenantIds.contains(entry.tenantKey().toString())) {
            return true;
          }
          batchNotFull[0] =
              visitJob(invertedPriorityAndJob.second().inner().getValue(), callback::apply);
          return batchNotFull[0];
        });
    return batchNotFull[0];
  }

  /**
   * Phase 2 — yields all pre-8.10 jobs from the legacy {@code JOB_ACTIVATABLE} column family.
   *
   * <p>Legacy jobs were written by Zeebe brokers prior to 8.10. They have no explicit priority and
   * are treated as priority = 0. Because job keys are monotonically increasing, legacy jobs always
   * have lower job keys than any 8.10 job. Draining them here — before the priority = 0 new jobs
   * served in Phase 3 — preserves the intended {@code (priority DESC, jobKey ASC)} activation order
   * across the upgrade boundary.
   *
   * <p>The {@code deleteIfExists} call in {@link #makeJobNotActivatable} ensures that a legacy job
   * is removed from this CF the first time it is deactivated after the upgrade.
   *
   * @return {@code true} if the batch still wants more jobs; {@code false} if the batch is full
   */
  private boolean visitLegacyActivatableJobs(
      final List<String> tenantIds, final BiFunction<Long, JobRecord, Boolean> callback) {
    final var batchNotFull = new boolean[] {true};
    activatableColumnFamily.whileEqualPrefix(
        jobTypeKey,
        entry -> {
          if (!tenantIds.contains(entry.tenantKey().toString())) {
            return true;
          }
          batchNotFull[0] =
              visitJob(entry.wrappedKey().second().inner().getValue(), callback::apply);
          return batchNotFull[0];
        });
    return batchNotFull[0];
  }

  /**
   * Phase 3 — yields all priority ≤ 0 jobs from {@code JOB_ACTIVATABLE_BY_PRIORITY}.
   *
   * <p>Uses the three-argument {@link
   * io.camunda.zeebe.db.ColumnFamily#whileEqualPrefix(io.camunda.zeebe.db.DbKey,
   * io.camunda.zeebe.db.DbKey, io.camunda.zeebe.db.KeyValuePairVisitor)} overload to seek directly
   * to the start of the priority = 0 range rather than re-scanning the priority > 0 entries already
   * served in Phase 1.
   *
   * <p>The seek key is constructed by setting {@code invertedPriorityKey = Integer.MAX_VALUE} (the
   * encoded form of priority = 0), {@code jobKey = 0} and {@code tenantIdKey = ""} — the
   * lexicographic minimum within that range — so the iterator lands at the first real entry at or
   * after that position.
   *
   * <p>Assumes {@code jobTypeKey} is already set by the caller (i.e., from {@link
   * #forEachActivatableJobs}).
   */
  private void visitNonPositivePriorityJobs(
      final List<String> tenantIds, final BiFunction<Long, JobRecord, Boolean> callback) {
    // Seek-key setup mutates shared mutable fields (same pattern as makeJobActivatable and
    // makeJobNotActivatable). After this method returns, invertedPriorityKey, jobKey, and
    // tenantIdKey hold values from the seek or from the last iteration. Callers of write
    // operations must re-wrap jobKey before use. This is the established contract in this class.
    invertedPriorityKey.wrapInt(Integer.MAX_VALUE); // Integer.MAX_VALUE encodes priority = 0
    jobKey.wrapLong(0L);
    tenantIdKey.wrapString("");
    priorityActivatableColumnFamily.whileEqualPrefix(
        jobTypeKey,
        tenantAwarePriorityKey,
        entry -> {
          if (!tenantIds.contains(entry.tenantKey().toString())) {
            return true;
          }
          final var invertedPriorityAndJob = entry.wrappedKey().second();
          return visitJob(invertedPriorityAndJob.second().inner().getValue(), callback::apply);
        });
  }

  boolean visitJob(final long jobKey, final BiPredicate<Long, JobRecord> callback) {
    final JobRecord job = getJob(jobKey);
    if (job == null) {
      LOG.warn("Expected to find job with key {}, but no job found", jobKey);
      return true; // we want to continue with the iteration
    }
    return callback.test(jobKey, job);
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

  private void makeJobActivatable(
      final DirectBuffer type, final long key, final String tenantId, final int priority) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);
    EnsureUtil.ensureNotNullOrEmpty("tenantId", tenantId);

    jobTypeKey.wrapBuffer(type);
    jobKey.wrapLong(key);
    tenantIdKey.wrapString(tenantId);
    // overflow for negative priorities is intentional (see invertedPriorityKey field comment above)
    invertedPriorityKey.wrapInt(Integer.MAX_VALUE - priority);
    // upsert because a failed job with retries can be made activatable multiple times
    priorityActivatableColumnFamily.upsert(tenantAwarePriorityKey, DbNil.INSTANCE);
  }

  // makeJobNotActivatable does NOT set jobKey. Callers are responsible for setting it
  // (via jobKey.wrapLong or via updateJobRecord) before calling it.
  private void makeJobNotActivatable(final JobRecord record) {
    makeJobNotActivatable(record.getTypeBuffer(), record.getTenantId(), record.getPriority());
  }

  private void makeJobNotActivatable(
      final DirectBuffer type, final String tenantId, final int priority) {
    EnsureUtil.ensureNotNullOrEmpty("type", type);
    EnsureUtil.ensureNotNullOrEmpty("tenantid", tenantId);

    jobTypeKey.wrapBuffer(type);
    tenantIdKey.wrapString(tenantId);
    // overflow for negative priorities is intentional (see invertedPriorityKey field comment above)
    invertedPriorityKey.wrapInt(Integer.MAX_VALUE - priority);
    // Requires jobKey to already be set by the caller (directly or via updateJobRecord).
    // This method does not set jobKey because it is not passed as a parameter.
    priorityActivatableColumnFamily.deleteIfExists(tenantAwarePriorityKey);
    // Legacy CF cleanup: pre-8.10 jobs live in JOB_ACTIVATABLE; deleteIfExists is a no-op
    // when the key is absent. Do not remove this line!
    activatableColumnFamily.deleteIfExists(tenantAwareTypeJobKey);
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
}
