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

  // [[type, invertedPriority, key], tenant_id] => nil
  //   where invertedPriority = Long.MAX_VALUE - priority, so RocksDB's natural ascending
  //   byte-order iteration returns entries in priority DESC, jobKey ASC. Negative priorities
  //   encode correctly via two's-complement wraparound; see CF_DESIGN.md §Negative Priority
  // Handling.
  private final DbLong invertedPriorityKey;
  private final DbCompositeKey<DbString, DbCompositeKey<DbLong, DbForeignKey<DbLong>>>
      typePriorityJobKey;
  private final DbTenantAwareKey<
          DbCompositeKey<DbString, DbCompositeKey<DbLong, DbForeignKey<DbLong>>>>
      tenantAwareTypePriorityJobKey;
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbCompositeKey<DbLong, DbForeignKey<DbLong>>>>,
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

    invertedPriorityKey = new DbLong();
    typePriorityJobKey =
        new DbCompositeKey<>(jobTypeKey, new DbCompositeKey<>(invertedPriorityKey, fkJob));
    tenantAwareTypePriorityJobKey =
        new DbTenantAwareKey<>(tenantIdKey, typePriorityJobKey, PlacementType.SUFFIX);
    priorityActivatableColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_ACTIVATABLE_BY_PRIORITY,
            transactionContext,
            tenantAwareTypePriorityJobKey,
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
    createJob(key, record);
  }

  @Override
  public void activate(final long key, final JobRecord record) {
    final DirectBuffer type = record.getTypeBuffer();
    final String tenantId = record.getTenantId();
    final long deadline = record.getDeadline();

    validateParameters(type);
    EnsureUtil.ensureGreaterThan("deadline", deadline, 0);

    updateJobRecord(key, record);

    updateJobState(State.ACTIVATED);

    makeJobNotActivatable(key, record);

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
    makeJobNotActivatable(key, record);
  }

  @Override
  public void throwError(final long key, final JobRecord updatedValue) {
    updateJob(key, updatedValue, State.ERROR_THROWN);
    makeJobNotActivatable(key, updatedValue);
  }

  @Override
  public void delete(final long key, final JobRecord record) {
    final DirectBuffer type = record.getTypeBuffer();
    final String tenantId = record.getTenantId();

    jobKey.wrapLong(key);
    jobsColumnFamily.deleteExisting(jobKey);

    statesJobColumnFamily.deleteExisting(fkJob);

    makeJobNotActivatable(key, record);

    removeJobDeadline(key, record.getDeadline());
    removeJobBackoff(key, record.getRecurringTime());
  }

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

  private void createJob(final long key, final JobRecord record) {
    createJobRecord(key, record);
    initializeJobState();
    makeJobActivatable(key, record);
  }

  private void updateJob(final long key, final JobRecord updatedValue, final State newState) {
    final DirectBuffer type = updatedValue.getTypeBuffer();

    validateParameters(type);

    updateJobRecord(key, updatedValue);

    updateJobState(newState);

    if (newState == State.ACTIVATABLE) {
      makeJobActivatable(key, updatedValue);
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

  /**
   * Merges the legacy {@code JOB_ACTIVATABLE} CF with the new {@code JOB_ACTIVATABLE_BY_PRIORITY}
   * CF, visiting jobs in {@code (priority DESC, jobKey ASC)} order.
   *
   * <p>The legacy CF is drain-only in 8.10 - no 8.10 writes go there - and will be empty on fresh
   * installs. Pre-existing 8.9 entries are consumed inline by the merge without any startup
   * migration. Legacy entries carry no stored priority and are treated as implicit priority 0; at
   * parity with new priority=0 entries, lower jobKey wins, which naturally drains legacy first
   * because pre-8.10 jobKeys are always lower than 8.10 jobKeys.
   *
   * <p>The merge uses seek-resume cursors (one per CF, each backed by the three-argument {@code
   * whileEqualPrefix(prefix, startAt, visitor)} overload): each cursor holds the last-visited
   * entry; to advance, the cursor constructs a {@code startAt} key strictly after its last position
   * and re-seeks, capturing the first remaining entry. This supports input-side early termination -
   * when the caller's visitor returns {@code false} the loop exits without scanning the rest of
   * either CF - and never materialises the full queue in memory.
   *
   * <p>This is the closest existing idiom to the single-CF cursor-resume pattern in {@link
   * #forEachTimedOutEntry(long, io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex,
   * BiPredicate)}, extended here to two CFs merged in sort order.
   */
  @Override
  public void forEachActivatableJobs(
      final DirectBuffer type,
      final List<String> tenantIds,
      final BiFunction<Long, JobRecord, Boolean> callback) {
    jobTypeKey.wrapBuffer(type);

    final LegacyCursor legacy = new LegacyCursor();
    final PriorityCursor priority = new PriorityCursor();
    primeLegacyCursor(legacy);
    primePriorityCursor(priority);

    while (!legacy.exhausted || !priority.exhausted) {
      final boolean fromPriority = legacyShouldYieldToPriority(legacy, priority);
      final long visitingJobKey = fromPriority ? priority.jobKey : legacy.jobKey;
      final String visitingTenantId = fromPriority ? priority.tenantId : legacy.tenantId;

      // Tenant-filter the visit, not the advance: entries for other tenants are still stepped
      // over so their cursor progresses. Cursors advance independently, so skipping a visit in
      // one CF never blocks the other from producing entries.
      if (tenantIds.contains(visitingTenantId) && !visitJob(visitingJobKey, callback::apply)) {
        return;
      }

      if (fromPriority) {
        advancePriorityCursor(priority);
      } else {
        advanceLegacyCursor(legacy);
      }
    }
  }

  /**
   * Decides which cursor yields the next entry in {@code (priority DESC, jobKey ASC)} order. The
   * legacy cursor always represents implicit priority=0.
   *
   * <p>Two's-complement self-inverse recovers the original priority from the inverted key value
   * across the full signed-long range, including {@code Long.MIN_VALUE} and {@code Long.MAX_VALUE}
   * (see CF_DESIGN.md §Negative Priority Handling - the subtraction deliberately exploits
   * wraparound; do not "fix" it by switching to unsigned arithmetic).
   */
  private static boolean legacyShouldYieldToPriority(
      final LegacyCursor legacy, final PriorityCursor priority) {
    if (priority.exhausted) {
      return false;
    }
    if (legacy.exhausted) {
      return true;
    }
    final long actualPriority = Long.MAX_VALUE - priority.invertedPriority;
    if (actualPriority > 0L) {
      return true;
    }
    if (actualPriority < 0L) {
      return false;
    }
    // Parity at priority=0: the cursor with the lower jobKey yields first. Pre-8.10 legacy
    // jobKeys are disjoint from and strictly less than 8.10 jobKeys, so legacy drains fully
    // before any new priority=0 entry surfaces.
    return priority.jobKey < legacy.jobKey;
  }

  private void primeLegacyCursor(final LegacyCursor cursor) {
    // Null startAt is equivalent to seeking to the prefix start; see TransactionalColumnFamily
    // forEachInPrefix which substitutes prefix for null.
    readFirstAfterLegacyStartAt(cursor, null);
  }

  private void advanceLegacyCursor(final LegacyCursor cursor) {
    // Seek strictly after the last-consumed entry. All jobKeys are globally unique, so jobKey+1
    // safely skips the consumed entry regardless of tenant (which is SUFFIX-placed in the key).
    // Guard against overflow at Long.MAX_VALUE (practically unreachable via Zeebe's key
    // generation but defensive here so correctness does not depend on production invariants).
    if (cursor.jobKey == Long.MAX_VALUE) {
      cursor.exhausted = true;
      return;
    }
    jobKey.wrapLong(cursor.jobKey + 1L);
    tenantIdKey.wrapString("");
    readFirstAfterLegacyStartAt(cursor, tenantAwareTypeJobKey);
  }

  private void readFirstAfterLegacyStartAt(
      final LegacyCursor cursor,
      final DbTenantAwareKey<DbCompositeKey<DbString, DbForeignKey<DbLong>>> startAt) {
    cursor.exhausted = true;
    activatableColumnFamily.whileEqualPrefix(
        jobTypeKey,
        startAt,
        (key, nil) -> {
          cursor.jobKey = key.wrappedKey().second().inner().getValue();
          cursor.tenantId = key.tenantKey().toString();
          cursor.exhausted = false;
          return false;
        });
  }

  private void primePriorityCursor(final PriorityCursor cursor) {
    readFirstAfterPriorityStartAt(cursor, null);
  }

  private void advancePriorityCursor(final PriorityCursor cursor) {
    // Seek strictly after the last-consumed entry within the same (type, invertedPriority) slice.
    // If no entry exists at jobKey+1 for this invertedPriority, RocksDB naturally rolls over to
    // the next invertedPriority (i.e., lower priority) - which is the desired next position.
    // Overflow guard: see advanceLegacyCursor.
    if (cursor.jobKey == Long.MAX_VALUE) {
      cursor.exhausted = true;
      return;
    }
    invertedPriorityKey.wrapLong(cursor.invertedPriority);
    jobKey.wrapLong(cursor.jobKey + 1L);
    tenantIdKey.wrapString("");
    readFirstAfterPriorityStartAt(cursor, tenantAwareTypePriorityJobKey);
  }

  private void readFirstAfterPriorityStartAt(
      final PriorityCursor cursor,
      final DbTenantAwareKey<DbCompositeKey<DbString, DbCompositeKey<DbLong, DbForeignKey<DbLong>>>>
          startAt) {
    cursor.exhausted = true;
    priorityActivatableColumnFamily.whileEqualPrefix(
        jobTypeKey,
        startAt,
        (key, nil) -> {
          cursor.invertedPriority = key.wrappedKey().second().first().getValue();
          cursor.jobKey = key.wrappedKey().second().second().inner().getValue();
          cursor.tenantId = key.tenantKey().toString();
          cursor.exhausted = false;
          return false;
        });
  }

  private static final class LegacyCursor {
    long jobKey;
    String tenantId;
    boolean exhausted = true;
  }

  private static final class PriorityCursor {
    long invertedPriority;
    long jobKey;
    String tenantId;
    boolean exhausted = true;
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

  private void makeJobActivatable(final long key, final JobRecord record) {
    final DirectBuffer type = record.getTypeBuffer();
    final String tenantId = record.getTenantId();
    EnsureUtil.ensureNotNullOrEmpty("type", type);
    EnsureUtil.ensureNotNullOrEmpty("tenantId", tenantId);

    jobTypeKey.wrapBuffer(type);
    jobKey.wrapLong(key);
    tenantIdKey.wrapString(tenantId);
    invertedPriorityKey.wrapLong(Long.MAX_VALUE - record.getPriority());
    // Need to upsert here because jobs can be marked as failed (and thus made activatable)
    // without activating them first.
    //
    // The legacy JOB_ACTIVATABLE CF is never written by 8.10; it only drains pre-existing
    // 8.9 entries via the k-way merge read path (see CF_DESIGN.md §Write Path).
    priorityActivatableColumnFamily.upsert(tenantAwareTypePriorityJobKey, DbNil.INSTANCE);
  }

  private void makeJobNotActivatable(final long key, final JobRecord record) {
    final DirectBuffer type = record.getTypeBuffer();
    final String tenantId = record.getTenantId();
    EnsureUtil.ensureNotNullOrEmpty("type", type);
    EnsureUtil.ensureNotNullOrEmpty("tenantId", tenantId);

    jobTypeKey.wrapBuffer(type);
    jobKey.wrapLong(key);
    tenantIdKey.wrapString(tenantId);
    invertedPriorityKey.wrapLong(Long.MAX_VALUE - record.getPriority());
    // Soft-delete from both CFs — the job may be in either (legacy if 8.9 era, priority if 8.10).
    // No CF tracking per job; mirrors the DbMessageState multi-index pattern.
    activatableColumnFamily.deleteIfExists(tenantAwareTypeJobKey);
    priorityActivatableColumnFamily.deleteIfExists(tenantAwareTypePriorityJobKey);
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
