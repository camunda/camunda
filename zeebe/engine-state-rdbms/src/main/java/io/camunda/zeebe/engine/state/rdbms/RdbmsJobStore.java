/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static io.camunda.zeebe.engine.state.rdbms.Payloads.serialize;
import static io.camunda.zeebe.engine.state.rdbms.Payloads.wrapCopy;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.instance.JobStore;
import io.camunda.zeebe.engine.state.rdbms.JobMapper.JobRow;
import io.camunda.zeebe.engine.state.rdbms.OrderedScan.Cursor;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobKind;
import java.util.List;
import java.util.Objects;
import java.util.function.LongPredicate;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Stores the job state in one relational table, through the partition's {@link EntitySession}: a
 * row per job, holding its record, its state and its lookup entries as nullable columns (see the
 * changeset of {@code CAM_RU_JOB}). The lookups are visited with an {@link OrderedScan} over their
 * index, which merges the committed rows with the transaction's staged writes.
 *
 * <p>A row holds one entry per lookup, where RocksDB could hold several per job. The current event
 * appliers never enter a second one: a job gets a deadline only on activation from ACTIVATABLE,
 * which has none, or after the strict removal of the old one, and loses it with the record's
 * deadline on every way out of ACTIVATED; it gets a backoff only on a failure with backoff from
 * ACTIVATED and loses it on recurring or deletion; it is entered for activation only from a state
 * without entry (created, ACTIVATED, FAILED, SUSPENDED, WAITING_FOR_SECRET_RESOLUTION) and a
 * priority update clears the old entry first. More entries per job only come from older versions,
 * whose events a fresh store never replays. Entering a second one therefore fails with an {@link
 * IllegalStateException} instead of overwriting the first.
 *
 * <p>Like RocksDB, a lookup entry is cleared only with the value it is stored under, and the lookup
 * columns aren't derived from the record: the record keeps its deadline after the job left
 * ACTIVATED, while the job has no deadline entry anymore.
 *
 * <p>Differences to the RocksDB store, none of which the current appliers can observe:
 *
 * <ul>
 *   <li>Deleting a job deletes its lookup entries with its row. The appliers clear them with the
 *       record's values right after the deletion, which are the stored ones, so RocksDB has no
 *       entries left either.
 *   <li>A lookup entry of a job without row isn't stored. RocksDB would store such an entry, but
 *       its visitors skip it, as there is no job to visit.
 *   <li>The legacy activation lookup of the brokers before 8.10 doesn't exist: only the appliers
 *       released before 8.10 enter jobs there, see {@link #setLegacyActivatable}.
 *   <li>The jobs of a process instance are the rows with its {@code process_instance_key}, so
 *       {@link #addToProcessInstance} and {@link #removeFromProcessInstance} write nothing. RocksDB
 *       misses only the jobs created before 8.10 and never suspended, which a fresh store doesn't
 *       have: every job is created by the JobCreatedV3 applier, which enters it. The only reader
 *       checks for SUSPENDED anyway.
 * </ul>
 *
 * <p>{@link #getJob} returns a shared record, which the next {@code getJob} overwrites, like the
 * RocksDB store; the rows themselves are never shared with callers.
 */
public final class RdbmsJobStore implements JobStore {

  static final EntityType<Long, JobRow> JOB =
      new EntityType<>(JobMapper.class, "Job", "job", JobRow::key);

  /** The smallest first page of an activation scan: workers often ask for a few jobs only. */
  private static final int MIN_ACTIVATION_PAGE_SIZE = 16;

  /** The first page of the deadline, backoff and process instance scans. */
  private static final int SCAN_PAGE_SIZE = 32;

  private final EntitySession session;
  private final int partitionId;
  private final OrderedScan<JobRow, JobMapper> activationScan;
  private final OrderedScan<JobRow, JobMapper> deadlineScan;
  private final OrderedScan<JobRow, JobMapper> backoffScan;
  private final OrderedScan<JobRow, JobMapper> processInstanceScan;
  private final JobRecord jobRecordToRead = new JobRecord();
  private final JobRecord jobRecordToWrite = new JobRecord();

  RdbmsJobStore(final EntitySession session) {
    this.session = session;
    partitionId = session.partitionId();
    activationScan =
        new OrderedScan<>(
            session, JOB, JobMapper.class, row -> Objects.requireNonNull(row.invertedPriority()));
    deadlineScan =
        new OrderedScan<>(
            session, JOB, JobMapper.class, row -> Objects.requireNonNull(row.deadline()));
    backoffScan =
        new OrderedScan<>(
            session, JOB, JobMapper.class, row -> Objects.requireNonNull(row.backoff()));
    processInstanceScan =
        new OrderedScan<>(session, JOB, JobMapper.class, JobRow::processInstanceKey);
  }

  @Override
  public @Nullable JobRecord getJob(final long key) {
    final var row = session.get(JOB, key);
    return row == null ? null : wrapCopy(jobRecordToRead, row.payload());
  }

  @Override
  public @Nullable State getState(final long key) {
    final var row = session.get(JOB, key);
    return row == null ? null : State.fromValue((byte) row.state());
  }

  @Override
  public boolean exists(final long key) {
    return session.get(JOB, key) != null;
  }

  @Override
  public void insertJob(final long key, final JobRecord record, final State state) {
    final long processInstanceKey = record.getProcessInstanceKey();
    final long rootProcessInstanceKey = record.getRootProcessInstanceKey();
    session.insert(
        JOB,
        toRow(
            key,
            state,
            processInstanceKey,
            rootProcessInstanceKey > 0 ? rootProcessInstanceKey : processInstanceKey,
            record));
  }

  @Override
  public void updateJob(final long key, final JobRecord record) {
    final var row = existing(key, "update");
    session.update(
        JOB,
        row.withRecord(
            toRow(
                key,
                State.fromValue((byte) row.state()),
                row.processInstanceKey(),
                row.rootProcessInstanceKey(),
                record)));
  }

  @Override
  public void updateState(final long key, final State state) {
    session.update(JOB, existing(key, "update the state of").withState(state.value()));
  }

  @Override
  public void deleteJob(final long key) {
    existing(key, "delete");
    session.delete(JOB, key);
  }

  @Override
  public void setActivatable(
      final long key, final DirectBuffer type, final String tenantId, final int priority) {
    final var row = session.get(JOB, key);
    if (row == null) {
      return;
    }
    final String activatableType = bufferAsString(type);
    final long invertedPriority = invertedPriority(priority);
    if (!tenantId.equals(row.tenantId())) {
      throw new IllegalStateException(
          "Expected to make job %d of tenant '%s' activatable for tenant '%s', but a job is only activatable for its own tenant"
              .formatted(key, row.tenantId(), tenantId));
    }
    if (row.activatableType() != null) {
      if (isActivatableAs(row, activatableType, tenantId, invertedPriority)) {
        // setting the same entry again is allowed
        return;
      }
      throw new IllegalStateException(
          "Expected to make job %d activatable for type '%s' with priority %d, but it is already activatable for type '%s' with priority %d"
              .formatted(
                  key,
                  activatableType,
                  priority,
                  row.activatableType(),
                  -Objects.requireNonNull(row.invertedPriority())));
    }
    session.update(JOB, row.withActivation(activatableType, invertedPriority));
  }

  /**
   * Not supported: only the event appliers released before 8.10 enter jobs in the legacy activation
   * lookup, and a fresh relational store never replays their events.
   */
  @Override
  public void setLegacyActivatable(final long key, final DirectBuffer type, final String tenantId) {
    throw new UnsupportedOperationException(
        "Expected to enter job %d in the legacy activation lookup of the brokers before 8.10, but the relational engine state has none"
            .formatted(key));
  }

  @Override
  public void clearActivatable(
      final long key, final DirectBuffer type, final String tenantId, final int priority) {
    final var row = session.get(JOB, key);
    if (row != null
        && isActivatableAs(row, bufferAsString(type), tenantId, invertedPriority(priority))) {
      session.update(JOB, row.withActivation(null, null));
    }
  }

  @Override
  public void visitActivatableJobs(
      final DirectBuffer type,
      final List<String> tenantIds,
      final int limitHint,
      final LongPredicate visitor) {
    if (tenantIds.isEmpty()) {
      return;
    }
    final String activatableType = bufferAsString(type);
    activationScan.visit(
        null,
        Math.max(limitHint * 2, MIN_ACTIVATION_PAGE_SIZE),
        row -> activatableType.equals(row.activatableType()) && tenantIds.contains(row.tenantId()),
        (mapper, cursor, inclusive, limit) ->
            mapper.selectActivatableJobs(
                partitionId, activatableType, tenantIds, cursor, inclusive, limit),
        row -> visitor.test(row.key()));
  }

  @Override
  public void setDeadline(final long key, final long deadline) {
    final var row = session.get(JOB, key);
    if (row == null) {
      return;
    }
    if (row.deadline() != null) {
      throw new IllegalStateException(
          "Expected to set deadline %d of job %d, but it already has deadline %d"
              .formatted(deadline, key, row.deadline()));
    }
    session.update(JOB, row.withDeadline(deadline));
  }

  @Override
  public void clearDeadline(final long key, final long deadline) {
    final var row = session.get(JOB, key);
    if (row != null && Objects.equals(row.deadline(), deadline)) {
      session.update(JOB, row.withDeadline(null));
    }
  }

  @Override
  public void clearExistingDeadline(final long key, final long deadline) {
    final var row = session.get(JOB, key);
    if (row == null || !Objects.equals(row.deadline(), deadline)) {
      throw new IllegalStateException(
          "Expected to remove deadline %d of job %d, but the job has %s"
              .formatted(
                  deadline, key, row == null ? "no row" : "deadline %s".formatted(row.deadline())));
    }
    session.update(JOB, row.withDeadline(null));
  }

  @Override
  public boolean hasDeadline(final long key, final long deadline) {
    final var row = session.get(JOB, key);
    return row != null && Objects.equals(row.deadline(), deadline);
  }

  @Override
  public @Nullable DeadlineIndex visitTimedOutJobs(
      final long timestamp, final @Nullable DeadlineIndex startAt, final LongPredicate visitor) {
    final var stoppedAt =
        deadlineScan.visit(
            startAt == null ? null : new Cursor(startAt.deadline(), startAt.key()),
            SCAN_PAGE_SIZE,
            row -> row.deadline() != null && row.deadline() < timestamp,
            (mapper, cursor, inclusive, limit) ->
                mapper.selectJobsByDeadline(partitionId, timestamp, cursor, inclusive, limit),
            row -> visitor.test(row.key()));
    return stoppedAt == null
        ? null
        : new DeadlineIndex(Objects.requireNonNull(stoppedAt.deadline()), stoppedAt.key());
  }

  @Override
  public void removeDeadlinesIf(final EntryPredicate predicate) {
    deadlineScan.visit(
        null,
        SCAN_PAGE_SIZE,
        row -> row.deadline() != null,
        (mapper, cursor, inclusive, limit) ->
            mapper.selectJobsByDeadline(partitionId, null, cursor, inclusive, limit),
        row -> {
          final long deadline = Objects.requireNonNull(row.deadline());
          if (predicate.test(row.key(), deadline)) {
            clearDeadline(row.key(), deadline);
          }
          return true;
        });
  }

  @Override
  public void setBackoff(final long key, final long backoff) {
    final var row = session.get(JOB, key);
    if (row == null) {
      return;
    }
    if (row.backoff() != null) {
      throw new IllegalStateException(
          "Expected to set backoff %d of job %d, but it already has backoff %d"
              .formatted(backoff, key, row.backoff()));
    }
    session.update(JOB, row.withBackoff(backoff));
  }

  @Override
  public void clearBackoff(final long key, final long backoff) {
    final var row = session.get(JOB, key);
    if (row != null && Objects.equals(row.backoff(), backoff)) {
      session.update(JOB, row.withBackoff(null));
    }
  }

  @Override
  public long visitBackedOffJobs(final long timestamp, final LongPredicate visitor) {
    final var stoppedAt =
        backoffScan.visit(
            null,
            SCAN_PAGE_SIZE,
            row -> row.backoff() != null,
            (mapper, cursor, inclusive, limit) ->
                mapper.selectJobsByBackoff(partitionId, cursor, inclusive, limit),
            row -> Objects.requireNonNull(row.backoff()) <= timestamp && visitor.test(row.key()));
    return stoppedAt == null ? -1L : Objects.requireNonNull(stoppedAt.backoff());
  }

  @Override
  public void removeBackoffsIf(final EntryPredicate predicate) {
    backoffScan.visit(
        null,
        SCAN_PAGE_SIZE,
        row -> row.backoff() != null,
        (mapper, cursor, inclusive, limit) ->
            mapper.selectJobsByBackoff(partitionId, cursor, inclusive, limit),
        row -> {
          final long backoff = Objects.requireNonNull(row.backoff());
          if (predicate.test(row.key(), backoff)) {
            clearBackoff(row.key(), backoff);
          }
          return true;
        });
  }

  /** Every job row has its process instance key, see the class documentation. */
  @Override
  public void addToProcessInstance(final long processInstanceKey, final long key) {}

  /** Every job row has its process instance key, see the class documentation. */
  @Override
  public void removeFromProcessInstance(final long processInstanceKey, final long key) {}

  @Override
  public void visitJobsOfProcessInstance(
      final long processInstanceKey, final long startAtKey, final LongPredicate visitor) {
    processInstanceScan.visit(
        startAtKey < 0 ? null : new Cursor(processInstanceKey, startAtKey),
        SCAN_PAGE_SIZE,
        row -> row.processInstanceKey() == processInstanceKey,
        (mapper, cursor, inclusive, limit) ->
            mapper.selectJobsOfProcessInstance(
                partitionId, processInstanceKey, cursor, inclusive, limit),
        row -> visitor.test(row.key()));
  }

  private JobRow existing(final long key, final String action) {
    final var row = session.get(JOB, key);
    if (row == null) {
      throw new IllegalStateException(
          "Expected to %s job %d, but it does not exist".formatted(action, key));
    }
    return row;
  }

  /** A new row without lookup entries. */
  private JobRow toRow(
      final long key,
      final State state,
      final long processInstanceKey,
      final long rootProcessInstanceKey,
      final JobRecord record) {
    jobRecordToWrite.wrapWithoutVariables(record);
    final String worker = record.getWorker();
    return new JobRow(
        key,
        state.value(),
        record.getType(),
        record.getTenantId(),
        null,
        null,
        null,
        null,
        processInstanceKey,
        rootProcessInstanceKey,
        record.getElementInstanceKey(),
        record.getBpmnProcessId(),
        record.getElementId(),
        jobKindCode(record.getJobKind()),
        record.getRetries(),
        worker.isEmpty() ? null : worker,
        serialize(jobRecordToWrite));
  }

  private static boolean isActivatableAs(
      final JobRow row,
      final String activatableType,
      final String tenantId,
      final long invertedPriority) {
    return activatableType.equals(row.activatableType())
        && tenantId.equals(row.tenantId())
        && Objects.equals(row.invertedPriority(), invertedPriority);
  }

  /** Ascending order serves the highest priority first, like RocksDB's inverted priority. */
  private static long invertedPriority(final int priority) {
    return -(long) priority;
  }

  /** A stable code of the job kind, which the row keeps for readability. */
  private static short jobKindCode(final JobKind jobKind) {
    return switch (jobKind) {
      case BPMN_ELEMENT -> 0;
      case EXECUTION_LISTENER -> 1;
      case TASK_LISTENER -> 2;
      case AD_HOC_SUB_PROCESS -> 3;
    };
  }
}
