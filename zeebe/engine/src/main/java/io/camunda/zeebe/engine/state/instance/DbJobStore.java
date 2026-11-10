/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.KeyVisitor;
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
import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongPredicate;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Stores the job state in RocksDB column families: the jobs, their states, and one column family
 * per lookup (activation by priority, legacy activation, deadlines, backoffs, jobs by process
 * instance), whose keys hold the indexed value and the job key.
 *
 * <p>Besides the activation by priority, it keeps the {@code JOB_ACTIVATABLE} column family of the
 * brokers before 8.10, see {@link #visitActivatableJobs}.
 *
 * <p>Reads return the column family's value instances, which the next read of the same column
 * family overwrites. The key fields are shared by all column families and operations: an operation
 * wraps every key part it uses, and a visitor that reads from the store re-wraps the keys of the
 * entry being visited with the same values.
 */
public final class DbJobStore implements JobStore {

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
      deprecatedActivatableColumnFamily;

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

  /**
   * Secondary index: {@code (processInstanceKey, jobKey) → ∅}; supports prefix iteration to find
   * jobs of a process instance. {@link JobStateImpl} decides which jobs it holds.
   */
  private final DbLong jobIndexProcessInstanceKey;

  private final DbCompositeKey<DbLong, DbForeignKey<DbLong>> processInstanceJobKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbForeignKey<DbLong>>, DbNil>
      jobsByProcessInstanceColumnFamily;

  /** In-memory, per-partition memory that the legacy JOB_ACTIVATABLE CF is globally drained. */
  private volatile boolean isLegacyCfDrained = false;

  public DbJobStore(
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
    deprecatedActivatableColumnFamily =
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

    jobIndexProcessInstanceKey = new DbLong();
    processInstanceJobKey = new DbCompositeKey<>(jobIndexProcessInstanceKey, fkJob);
    jobsByProcessInstanceColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOBS_BY_PROCESS_INSTANCE,
            transactionContext,
            processInstanceJobKey,
            DbNil.INSTANCE);
  }

  @Override
  public @Nullable JobRecord getJob(final long key) {
    jobKey.wrapLong(key);
    final JobRecordValue jobState = jobsColumnFamily.get(jobKey);
    return jobState == null ? null : jobState.getRecord();
  }

  @Override
  public @Nullable State getState(final long key) {
    jobKey.wrapLong(key);
    final JobStateValue storedState = statesJobColumnFamily.get(fkJob);
    return storedState == null ? null : storedState.getState();
  }

  @Override
  public boolean exists(final long key) {
    jobKey.wrapLong(key);
    return jobsColumnFamily.exists(jobKey);
  }

  @Override
  public void insertJob(final long key, final JobRecord record, final State state) {
    jobKey.wrapLong(key);
    // do not persist variables in job state
    jobRecordToWrite.setRecordWithoutVariables(record);
    jobsColumnFamily.insert(jobKey, jobRecordToWrite);

    jobState.setState(state);
    statesJobColumnFamily.insert(fkJob, jobState);
  }

  @Override
  public void updateJob(final long key, final JobRecord record) {
    jobKey.wrapLong(key);
    // do not persist variables in job state
    jobRecordToWrite.setRecordWithoutVariables(record);
    jobsColumnFamily.update(jobKey, jobRecordToWrite);
  }

  @Override
  public void updateState(final long key, final State state) {
    jobKey.wrapLong(key);
    jobState.setState(state);
    statesJobColumnFamily.update(fkJob, jobState);
  }

  @Override
  public void deleteJob(final long key) {
    jobKey.wrapLong(key);
    jobsColumnFamily.deleteExisting(jobKey);
    statesJobColumnFamily.deleteExisting(fkJob);
  }

  @Override
  public void setActivatable(
      final long key, final DirectBuffer type, final String tenantId, final int priority) {
    jobTypeKey.wrapBuffer(type);
    jobKey.wrapLong(key);
    tenantIdKey.wrapString(tenantId);
    // overflow for negative priorities is intentional (see invertedPriorityKey field comment above)
    invertedPriorityKey.wrapInt(Integer.MAX_VALUE - priority);
    // upsert because a failed job with retries can be made activatable multiple times
    priorityActivatableColumnFamily.upsert(tenantAwarePriorityKey, DbNil.INSTANCE);
  }

  /**
   * Writes only the legacy {@code JOB_ACTIVATABLE} column family, with no notion of priority, as
   * the brokers before 8.10 did.
   */
  @Override
  public void setLegacyActivatable(final long key, final DirectBuffer type, final String tenantId) {
    jobTypeKey.wrapBuffer(type);
    jobKey.wrapLong(key);
    tenantIdKey.wrapString(tenantId);
    // upsert because a failed job with retries can be made activatable multiple times
    deprecatedActivatableColumnFamily.upsert(tenantAwareTypeJobKey, DbNil.INSTANCE);
  }

  @Override
  public void clearActivatable(
      final long key, final DirectBuffer type, final String tenantId, final int priority) {
    jobTypeKey.wrapBuffer(type);
    jobKey.wrapLong(key);
    tenantIdKey.wrapString(tenantId);
    // overflow for negative priorities is intentional (see invertedPriorityKey field comment above)
    invertedPriorityKey.wrapInt(Integer.MAX_VALUE - priority);
    priorityActivatableColumnFamily.deleteIfExists(tenantAwarePriorityKey);
    // Legacy CF cleanup: pre-8.10 jobs live in JOB_ACTIVATABLE; deleteIfExists is a no-op
    // when the key is absent. Do not remove this line!
    deprecatedActivatableColumnFamily.deleteIfExists(tenantAwareTypeJobKey);
  }

  /**
   * Ignores the limit hint, and reads nothing ahead.
   *
   * <p>Three-phase sequential read ensures correct activation order across the 8.10 upgrade
   * boundary:
   *
   * <ul>
   *   <li>Phase 1: new jobs with priority > 0, highest first
   *   <li>Phase 2: legacy pre-8.10 jobs from JOB_ACTIVATABLE (implicit priority = 0, lower job
   *       keys)
   *   <li>Phase 3: new jobs with priority <= 0, highest first
   * </ul>
   *
   * Each phase returns true if the visitor still wants more jobs. Subsequent phases are skipped
   * when it doesn't, to avoid redundant state reads.
   *
   * <p>Once the legacy CF is globally drained, Phase 2 can never yield a job again, so Phases 1 and
   * 3 collapse into a single scan instead of two separate seeks.
   */
  @Override
  public void visitActivatableJobs(
      final DirectBuffer type,
      final List<String> tenantIds,
      final int limitHint,
      final ActivatableJobKeyVisitor visitor) {
    jobTypeKey.wrapBuffer(type);

    if (isLegacyCfDrained) {
      visitAllPriorityJobs(tenantIds, visitor);
    } else if (visitHighPriorityJobs(tenantIds, visitor)
        && visitLegacyActivatableJobs(type, tenantIds, visitor)) {
      visitNonPositivePriorityJobs(tenantIds, visitor);
    }
  }

  @Override
  public void setDeadline(final long key, final long deadline) {
    jobKey.wrapLong(key);
    deadlineKey.wrapLong(deadline);
    deadlinesColumnFamily.insert(deadlineJobKey, DbNil.INSTANCE);
  }

  @Override
  public void clearDeadline(final long key, final long deadline) {
    jobKey.wrapLong(key);
    deadlineKey.wrapLong(deadline);
    deadlinesColumnFamily.deleteIfExists(deadlineJobKey);
  }

  @Override
  public void clearExistingDeadline(final long key, final long deadline) {
    jobKey.wrapLong(key);
    deadlineKey.wrapLong(deadline);
    deadlinesColumnFamily.deleteExisting(deadlineJobKey);
  }

  @Override
  public boolean hasDeadline(final long key, final long deadline) {
    jobKey.wrapLong(key);
    deadlineKey.wrapLong(deadline);
    return deadlinesColumnFamily.exists(deadlineJobKey);
  }

  @Override
  public @Nullable DeadlineIndex visitTimedOutJobs(
      final long timestamp, final @Nullable DeadlineIndex startAt, final LongPredicate visitor) {

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
          final var isDue = deadline < timestamp;
          if (!isDue) {
            return false;
          }
          final var jobKey = key.second().inner().getValue();
          if (!visitor.test(jobKey)) {
            lastVisitedIndex.set(
                new DeadlineIndex(key.first().getValue(), key.second().inner().getValue()));
            return false;
          }
          return true;
        });

    return lastVisitedIndex.get();
  }

  @Override
  public void removeDeadlinesIf(final EntryPredicate predicate) {
    deadlinesColumnFamily.whileTrue(
        key -> {
          final var jobKey = key.second().inner().getValue();
          final var deadline = key.first().getValue();
          if (predicate.test(jobKey, deadline)) {
            // the predicate may read from the store, which reuses the key of this entry
            this.jobKey.wrapLong(jobKey);
            deadlineKey.wrapLong(deadline);
            deadlinesColumnFamily.deleteExisting(key);
          }
          return true;
        });
  }

  @Override
  public void setBackoff(final long key, final long backoff) {
    jobKey.wrapLong(key);
    backoffKey.wrapLong(backoff);
    backoffColumnFamily.insert(backoffJobKey, DbNil.INSTANCE);
  }

  @Override
  public void clearBackoff(final long key, final long backoff) {
    jobKey.wrapLong(key);
    backoffKey.wrapLong(backoff);
    backoffColumnFamily.deleteIfExists(backoffJobKey);
  }

  @Override
  public long visitBackedOffJobs(final long timestamp, final LongPredicate visitor) {
    nextBackOffDueDate = -1L;
    backoffColumnFamily.whileTrue(
        key -> {
          final long deadline = key.first().getValue();
          boolean consumed = false;
          if (deadline <= timestamp) {
            final long jobKey = key.second().inner().getValue();
            consumed = visitor.test(jobKey);
          }
          if (!consumed) {
            nextBackOffDueDate = deadline;
          }
          return consumed;
        });
    return nextBackOffDueDate;
  }

  @Override
  public void removeBackoffsIf(final EntryPredicate predicate) {
    backoffColumnFamily.forEachKey(
        (key) -> {
          final long backoff = backoffKey.getValue();
          final long backedOffJobKey = jobKey.getValue();
          if (predicate.test(backedOffJobKey, backoff)) {
            // the predicate may read from the store, which reuses the key of this entry
            backoffKey.wrapLong(backoff);
            jobKey.wrapLong(backedOffJobKey);
            backoffColumnFamily.deleteExisting(key);
          }
        });
  }

  @Override
  public void addToProcessInstance(final long processInstanceKey, final long key) {
    jobIndexProcessInstanceKey.wrapLong(processInstanceKey);
    jobKey.wrapLong(key);
    jobsByProcessInstanceColumnFamily.upsert(processInstanceJobKey, DbNil.INSTANCE);
  }

  @Override
  public void removeFromProcessInstance(final long processInstanceKey, final long key) {
    jobIndexProcessInstanceKey.wrapLong(processInstanceKey);
    jobKey.wrapLong(key);
    jobsByProcessInstanceColumnFamily.deleteIfExists(processInstanceJobKey);
  }

  @Override
  public void visitJobsOfProcessInstance(
      final long processInstanceKey, final long startAtKey, final LongPredicate visitor) {
    jobIndexProcessInstanceKey.wrapLong(processInstanceKey);
    jobKey.wrapLong(startAtKey);
    final var startAt = startAtKey < 0 ? null : processInstanceJobKey;
    // key-only visitor: every value in this index is DbNil, so reading them would be wasted work
    final KeyVisitor<DbCompositeKey<DbLong, DbForeignKey<DbLong>>> keyVisitor =
        key -> visitor.test(key.second().inner().getValue());
    jobsByProcessInstanceColumnFamily.whileEqualPrefix(
        jobIndexProcessInstanceKey, startAt, keyVisitor);
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
   * @return {@code true} if the visitor still wants more jobs; {@code false} if it doesn't
   */
  private boolean visitHighPriorityJobs(final List<String> tenantIds, final LongPredicate visitor) {
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
          batchNotFull[0] = visitor.test(invertedPriorityAndJob.second().inner().getValue());
          return batchNotFull[0];
        });
    return batchNotFull[0];
  }

  /**
   * Drained-path scan — yields all jobs of this type from {@code JOB_ACTIVATABLE_BY_PRIORITY} in
   * one pass, in descending priority order, with no priority=0 boundary check. Only used once
   * {@link #isLegacyCfDrained} is set.
   *
   * <p>Assumes {@code jobTypeKey} is already set by the caller (i.e., from {@link
   * #visitActivatableJobs}).
   */
  private void visitAllPriorityJobs(final List<String> tenantIds, final LongPredicate visitor) {
    priorityActivatableColumnFamily.whileEqualPrefix(
        jobTypeKey,
        entry -> {
          if (!tenantIds.contains(entry.tenantKey().toString())) {
            return true;
          }
          final var invertedPriorityAndJob = entry.wrappedKey().second();
          return visitor.test(invertedPriorityAndJob.second().inner().getValue());
        });
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
   * <p>The {@code deleteIfExists} call in {@link #clearActivatable} ensures that a legacy job is
   * removed from this CF the first time it is deactivated after the upgrade.
   *
   * <p>If this type's prefix yields no entries, a global emptiness check is performed. The check
   * uses a tenant-agnostic {@link ColumnFamily#isEmpty()} seek run <b>after</b> the prefix scan
   * returns. If the CF is empty for every type and tenant, {@link #isLegacyCfDrained} is set so
   * subsequent activations skip this phase entirely. A per-type empty result alone must never set
   * the flag.
   *
   * @return {@code true} if the visitor still wants more jobs; {@code false} if it doesn't
   */
  private boolean visitLegacyActivatableJobs(
      final DirectBuffer type, final List<String> tenantIds, final LongPredicate visitor) {
    final var batchNotFull = new boolean[] {true};
    final var visitedAny = new boolean[] {false};
    deprecatedActivatableColumnFamily.whileEqualPrefix(
        jobTypeKey,
        entry -> {
          visitedAny[0] = true; // an entry exists for this type prefix => CF is not globally empty
          if (!tenantIds.contains(entry.tenantKey().toString())) {
            return true;
          }
          batchNotFull[0] = visitor.test(entry.wrappedKey().second().inner().getValue());
          return batchNotFull[0];
        });
    // Only worth checking globally when this type's own prefix was empty; isEmpty() is the
    // authoritative global gate.
    if (!visitedAny[0]) {
      // isEmpty() finds the first entry in the whole CF regardless of type, and its visitor
      // wraps the shared jobTypeKey field with that entry before reporting non-empty. Phase 3
      // (visitNonPositivePriorityJobs) relies on jobTypeKey still holding the type queried by
      // this call, so it must be restored from the caller's untouched buffer before returning.
      final boolean isGloballyEmpty = deprecatedActivatableColumnFamily.isEmpty();
      jobTypeKey.wrapBuffer(type);
      if (isGloballyEmpty) {
        isLegacyCfDrained = true;
      }
    }
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
   * #visitActivatableJobs}).
   */
  private void visitNonPositivePriorityJobs(
      final List<String> tenantIds, final LongPredicate visitor) {
    // Seek-key setup mutates shared mutable fields. After this method returns,
    // invertedPriorityKey, jobKey, and tenantIdKey hold values from the seek or from the last
    // iteration; every operation wraps the key parts it uses.
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
          return visitor.test(invertedPriorityAndJob.second().inner().getValue());
        });
  }
}
