/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.security.core.authz.TenantAccess;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Leaves the job rows alone while the replay applies events whose changes the database already
 * contains (see {@link JdbcTransactionContext#isReplayingCommittedChanges()}): the mutators do
 * nothing, and the reads see the rows in the database's later state. Otherwise, every call is
 * delegated.
 */
@NullMarked
final class ReplaySkippingJobState implements MutableJobState {

  private final MutableJobState delegate;
  private final JdbcTransactionContext context;

  ReplaySkippingJobState(final MutableJobState delegate, final JdbcTransactionContext context) {
    this.delegate = delegate;
    this.context = context;
  }

  private boolean skip() {
    return context.isReplayingCommittedChanges();
  }

  @Override
  @Deprecated
  public void create(final long key, final JobRecord record) {
    if (!skip()) {
      delegate.create(key, record);
    }
  }

  @Override
  public void activate(final long key, final JobRecord record) {
    if (!skip()) {
      delegate.activate(key, record);
    }
  }

  @Override
  @Deprecated
  public void recurAfterBackoff(final long key, final JobRecord record) {
    if (!skip()) {
      delegate.recurAfterBackoff(key, record);
    }
  }

  @Override
  @Deprecated
  public void timeout(final long key, final JobRecord record) {
    if (!skip()) {
      delegate.timeout(key, record);
    }
  }

  @Override
  public void complete(final long key, final JobRecord record) {
    if (!skip()) {
      delegate.complete(key, record);
    }
  }

  @Override
  public void cancel(final long key, final JobRecord record) {
    if (!skip()) {
      delegate.cancel(key, record);
    }
  }

  @Override
  public void disable(final long key, final JobRecord record) {
    if (!skip()) {
      delegate.disable(key, record);
    }
  }

  @Override
  public void throwError(final long key, final JobRecord updatedValue) {
    if (!skip()) {
      delegate.throwError(key, updatedValue);
    }
  }

  @Override
  public void delete(final long key, final JobRecord record) {
    if (!skip()) {
      delegate.delete(key, record);
    }
  }

  @Override
  @Deprecated
  public void fail(final long key, final JobRecord updatedValue) {
    if (!skip()) {
      delegate.fail(key, updatedValue);
    }
  }

  @Override
  @Deprecated
  public void yield(final long key, final JobRecord updatedValue) {
    if (!skip()) {
      delegate.yield(key, updatedValue);
    }
  }

  @Override
  public void parkForSecretResolution(final long key, final JobRecord record) {
    if (!skip()) {
      delegate.parkForSecretResolution(key, record);
    }
  }

  @Override
  public void makeActivatableAfterSecretResolution(final long key) {
    if (!skip()) {
      delegate.makeActivatableAfterSecretResolution(key);
    }
  }

  @Override
  @Deprecated
  public void resolve(final long key, final JobRecord updatedValue) {
    if (!skip()) {
      delegate.resolve(key, updatedValue);
    }
  }

  /**
   * When skipping, returns null, as the delegate does for a job that does not exist, without
   * reading the job: the appliers calling it ignore the returned job.
   */
  @Override
  public @Nullable JobRecord updateJobRetries(final long jobKey, final int retries) {
    if (skip()) {
      return null;
    }
    return delegate.updateJobRetries(jobKey, retries);
  }

  @Override
  public void cleanupTimeoutsWithoutJobs() {
    if (!skip()) {
      delegate.cleanupTimeoutsWithoutJobs();
    }
  }

  @Override
  public void cleanupBackoffsWithoutJobs() {
    if (!skip()) {
      delegate.cleanupBackoffsWithoutJobs();
    }
  }

  @Override
  public void updateJobDeadline(final long jobKey, final long newDeadline) {
    if (!skip()) {
      delegate.updateJobDeadline(jobKey, newDeadline);
    }
  }

  @Override
  public void updateJobPriority(final long jobKey, final int newPriority) {
    if (!skip()) {
      delegate.updateJobPriority(jobKey, newPriority);
    }
  }

  @Override
  public void migrate(final long key, final JobRecord record) {
    if (!skip()) {
      delegate.migrate(key, record);
    }
  }

  @Override
  public void removeInvalidBackoffs(final LongPredicate canBackOff) {
    if (!skip()) {
      delegate.removeInvalidBackoffs(canBackOff);
    }
  }

  @Override
  public void insertJobRecordActivatable(final long key, final JobRecord record) {
    if (!skip()) {
      delegate.insertJobRecordActivatable(key, record);
    }
  }

  @Override
  public void updateJobRecord(final long key, final JobRecord updatedValue) {
    if (!skip()) {
      delegate.updateJobRecord(key, updatedValue);
    }
  }

  @Override
  public void updateJobState(final long key, final State newState) {
    if (!skip()) {
      delegate.updateJobState(key, newState);
    }
  }

  @Override
  public void removeJobDeadline(final long key, final long deadline) {
    if (!skip()) {
      delegate.removeJobDeadline(key, deadline);
    }
  }

  @Override
  public void makeJobActivatableByPriority(
      final DirectBuffer type, final long key, final String tenantId, final int priority) {
    if (!skip()) {
      delegate.makeJobActivatableByPriority(type, key, tenantId, priority);
    }
  }

  @Override
  public void makeJobNotActivatable(final long key, final JobRecord record) {
    if (!skip()) {
      delegate.makeJobNotActivatable(key, record);
    }
  }

  @Override
  public void addJobBackoff(final long job, final long backoff) {
    if (!skip()) {
      delegate.addJobBackoff(job, backoff);
    }
  }

  @Override
  public void removeJobBackoff(final long job, final long backoff) {
    if (!skip()) {
      delegate.removeJobBackoff(job, backoff);
    }
  }

  @Override
  public @Nullable DeadlineIndex forEachTimedOutEntry(
      final long executionTimestamp,
      final @Nullable DeadlineIndex startAt,
      final BiPredicate<Long, JobRecord> callback) {
    return delegate.forEachTimedOutEntry(executionTimestamp, startAt, callback);
  }

  @Override
  public boolean exists(final long jobKey) {
    return delegate.exists(jobKey);
  }

  @Override
  public State getState(final long key) {
    return delegate.getState(key);
  }

  @Override
  public boolean isInState(final long key, final State state) {
    return delegate.isInState(key, state);
  }

  @Override
  public void forEachActivatableJobs(
      final DirectBuffer type,
      final List<String> tenantIds,
      final BiFunction<Long, JobRecord, Boolean> callback) {
    delegate.forEachActivatableJobs(type, tenantIds, callback);
  }

  @Override
  public void forEachActivatableJobs(
      final DirectBuffer type,
      final List<String> tenantIds,
      final int limitHint,
      final ActivatableJobVisitor callback) {
    delegate.forEachActivatableJobs(type, tenantIds, limitHint, callback);
  }

  @Override
  public @Nullable JobRecord getJob(final long key) {
    return delegate.getJob(key);
  }

  @Override
  public @Nullable JobRecord getJob(final long key, final TenantAccess authorizedTenantIds) {
    return delegate.getJob(key, authorizedTenantIds);
  }

  @Override
  public boolean jobDeadlineExists(final long jobKey, final long deadline) {
    return delegate.jobDeadlineExists(jobKey, deadline);
  }

  @Override
  public long findBackedOffJobs(final long timestamp, final BiPredicate<Long, JobRecord> callback) {
    return delegate.findBackedOffJobs(timestamp, callback);
  }

  @Override
  public void forEachJobsByProcessInstance(
      final long processInstanceKey, final long startAtJobKey, final LongPredicate visitor) {
    delegate.forEachJobsByProcessInstance(processInstanceKey, startAtJobKey, visitor);
  }
}
