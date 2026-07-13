/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.layered.ReadOnlyView;
import io.camunda.zeebe.db.layered.ViewPublisher;
import io.camunda.zeebe.db.layered.typed.LayeredViewReader;
import io.camunda.zeebe.engine.processing.identity.AuthorizedTenants;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link JobState} for the asynchronous job-timeout checker over the layered state store's read
 * views (experimental; only wired when the layered-state flag is on): each {@link
 * #forEachTimedOutEntry} acquires the latest published {@link ReadOnlyView}, scans it, and releases
 * it — so the scan observes a consistent, slightly stale cut of the committed state instead of
 * reading a separate transaction context that would miss the buffered writes. Key encoding mirrors
 * {@link DbJobState} exactly; both read the same column families.
 *
 * <p>Only the deadline scan is supported — it is the only read the checker performs. The other
 * {@link JobState} reads belong to processors, which read the owner state.
 *
 * <p>View scans cannot seek, so a resumed scan ({@code startAt} non-null, after the checker yielded
 * on a full task result) re-reads the deadline index from the start and skips entries before the
 * resume point. Skipped entries are exactly the ones the checker already timed out in this pass, so
 * the linear re-position is bounded by the checker's own batch size.
 *
 * <p><b>Threading:</b> one instance per reader; the flyweights are shared across calls, so calls
 * must not overlap (the checker executes on a single actor, which guarantees exactly that).
 */
public final class LayeredViewJobState implements JobState {

  private static final Logger LOG = LoggerFactory.getLogger(LayeredViewJobState.class);
  private static final String UNSUPPORTED_MESSAGE =
      "expected only deadline scans on the layered view job state, but %s was called";

  private final ViewPublisher views;

  private final JobRecordValue jobRecord = new JobRecordValue();
  private final DbLong jobKey = new DbLong();
  private final DbForeignKey<DbLong> fkJob = new DbForeignKey<>(jobKey, ZbColumnFamilies.JOBS);
  private final DbLong deadline = new DbLong();
  private final DbCompositeKey<DbLong, DbForeignKey<DbLong>> deadlineJobKey =
      new DbCompositeKey<>(deadline, fkJob);

  private @Nullable DeadlineIndex lastVisitedIndex;

  public LayeredViewJobState(final ViewPublisher views) {
    this.views = Objects.requireNonNull(views, "views");
  }

  @Override
  public DeadlineIndex forEachTimedOutEntry(
      final long executionTimestamp,
      final DeadlineIndex startAt,
      final BiPredicate<Long, JobRecord> callback) {
    final ReadOnlyView view = views.acquireLatest();
    try {
      final var deadlines =
          new LayeredViewReader<>(
              view, ZbColumnFamilies.JOB_DEADLINES.name(), deadlineJobKey, DbNil.INSTANCE);
      final var jobs =
          new LayeredViewReader<>(view, ZbColumnFamilies.JOBS.name(), new DbLong(), jobRecord);
      lastVisitedIndex = null;

      deadlines.whileTrue(
          (key, nil) -> {
            final long deadline = key.first().getValue();
            if (deadline >= executionTimestamp) {
              // mirrors the owner path: only strictly-passed deadlines are due
              return false;
            }
            final long jobKey = key.second().inner().getValue();
            if (isBeforeResumePoint(deadline, jobKey, startAt)) {
              return true;
            }
            final JobRecordValue job = jobs.get(key.second().inner());
            if (job == null) {
              // unlike the owner path, a view is a consistent cut, so a deadline without its job
              // cannot be observed mid-removal; tolerate it anyway, mirroring DbJobState
              LOG.warn("Expected to find job with key {}, but no job found", jobKey);
              return true;
            }
            if (!callback.test(jobKey, job.getRecord())) {
              lastVisitedIndex = new DeadlineIndex(deadline, jobKey);
              return false;
            }
            return true;
          });

      return lastVisitedIndex;
    } finally {
      views.release(view);
    }
  }

  /** Whether the entry precedes {@code startAt} in the deadline index (resume is inclusive). */
  private static boolean isBeforeResumePoint(
      final long deadline, final long jobKey, final @Nullable DeadlineIndex startAt) {
    if (startAt == null) {
      return false;
    }
    return deadline < startAt.deadline()
        || (deadline == startAt.deadline() && jobKey < startAt.key());
  }

  @Override
  public boolean exists(final long jobKey) {
    throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE.formatted("exists"));
  }

  @Override
  public State getState(final long key) {
    throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE.formatted("getState"));
  }

  @Override
  public boolean isInState(final long key, final State state) {
    throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE.formatted("isInState"));
  }

  @Override
  public void forEachActivatableJobs(
      final DirectBuffer type,
      final List<String> tenantIds,
      final BiFunction<Long, JobRecord, Boolean> callback) {
    throw new UnsupportedOperationException(
        UNSUPPORTED_MESSAGE.formatted("forEachActivatableJobs"));
  }

  @Override
  public JobRecord getJob(final long key) {
    throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE.formatted("getJob"));
  }

  @Override
  public JobRecord getJob(final long key, final AuthorizedTenants authorizedTenantIds) {
    throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE.formatted("getJob"));
  }

  @Override
  public boolean jobDeadlineExists(final long jobKey, final long deadline) {
    throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE.formatted("jobDeadlineExists"));
  }

  @Override
  public long findBackedOffJobs(final long timestamp, final BiPredicate<Long, JobRecord> callback) {
    throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE.formatted("findBackedOffJobs"));
  }
}
