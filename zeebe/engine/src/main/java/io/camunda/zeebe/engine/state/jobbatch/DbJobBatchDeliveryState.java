/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobbatch;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.mutable.MutableJobBatchDeliveryState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;

public final class DbJobBatchDeliveryState implements MutableJobBatchDeliveryState {

  private final DbLong deliveryAttemptKey;
  private final PendingJobBatchDelivery pendingDeliveryToRead = new PendingJobBatchDelivery();
  private final PendingJobBatchDelivery pendingDeliveryToWrite = new PendingJobBatchDelivery();
  private final ColumnFamily<DbLong, PendingJobBatchDelivery> pendingDeliveryColumnFamily;

  private final DbLong deadlineKey;
  private final DbCompositeKey<DbLong, DbLong> deadlineAttemptKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil> deadlinesColumnFamily;

  public DbJobBatchDeliveryState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    deliveryAttemptKey = new DbLong();
    pendingDeliveryColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_BATCH_PENDING_DELIVERY,
            transactionContext,
            deliveryAttemptKey,
            pendingDeliveryToRead);

    deadlineKey = new DbLong();
    deadlineAttemptKey = new DbCompositeKey<>(deadlineKey, deliveryAttemptKey);
    deadlinesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_BATCH_PENDING_DELIVERY_DEADLINES,
            transactionContext,
            deadlineAttemptKey,
            DbNil.INSTANCE);
  }

  @Override
  public void storePendingDelivery(
      final long attemptKey,
      final String jobType,
      final long deliveryDeadline,
      final List<Long> jobKeys) {
    if (attemptKey <= 0 || deliveryDeadline <= 0 || jobKeys.isEmpty()) {
      return;
    }

    deliveryAttemptKey.wrapLong(attemptKey);
    pendingDeliveryToWrite.wrap(jobType, deliveryDeadline, jobKeys);
    pendingDeliveryColumnFamily.insert(deliveryAttemptKey, pendingDeliveryToWrite);

    deadlineKey.wrapLong(deliveryDeadline);
    deadlinesColumnFamily.insert(deadlineAttemptKey, DbNil.INSTANCE);
  }

  @Override
  public void removePendingDelivery(final long attemptKey) {
    if (attemptKey <= 0) {
      return;
    }

    deliveryAttemptKey.wrapLong(attemptKey);
    final var pending = pendingDeliveryColumnFamily.get(deliveryAttemptKey);
    if (pending == null) {
      return;
    }

    final long deadline = pending.getDeliveryDeadline();
    pendingDeliveryColumnFamily.deleteExisting(deliveryAttemptKey);

    if (deadline > 0) {
      deadlineKey.wrapLong(deadline);
      deadlinesColumnFamily.deleteIfExists(deadlineAttemptKey);
    }
  }

  @Override
  public Optional<PendingJobBatchDelivery> getPendingDelivery(final long attemptKey) {
    if (attemptKey <= 0) {
      return Optional.empty();
    }
    deliveryAttemptKey.wrapLong(attemptKey);
    final var pending = pendingDeliveryColumnFamily.get(deliveryAttemptKey);
    if (pending == null) {
      return Optional.empty();
    }
    // Copy out of the shared CF buffer before another read overwrites it.
    final var copy = new PendingJobBatchDelivery();
    copy.wrap(pending.getType(), pending.getDeliveryDeadline(), pending.getJobKeys());
    return Optional.of(copy);
  }

  @Override
  public DeliveryDeadlineIndex forEachTimedOutDelivery(
      final long executionTimestamp,
      final DeliveryDeadlineIndex startAt,
      final BiPredicate<Long, PendingJobBatchDelivery> callback) {
    final DbCompositeKey<DbLong, DbLong> startAtKey;
    if (startAt != null) {
      deadlineKey.wrapLong(startAt.deadline());
      deliveryAttemptKey.wrapLong(startAt.deliveryAttemptKey());
      startAtKey = deadlineAttemptKey;
    } else {
      startAtKey = null;
    }

    final var lastVisitedIndex = new AtomicReference<DeliveryDeadlineIndex>();
    deadlinesColumnFamily.whileTrue(
        startAtKey,
        (key, value) -> {
          final var deadline = key.first().getValue();
          if (deadline >= executionTimestamp) {
            return false;
          }

          final long attemptKey = key.second().getValue();
          deliveryAttemptKey.wrapLong(attemptKey);
          final var pending = pendingDeliveryColumnFamily.get(deliveryAttemptKey);
          if (pending == null || pending.getDeliveryDeadline() != deadline) {
            deadlinesColumnFamily.deleteExisting(key);
            return true;
          }

          final var copy = new PendingJobBatchDelivery();
          copy.wrap(pending.getType(), pending.getDeliveryDeadline(), pending.getJobKeys());
          final boolean continueIteration = callback.test(attemptKey, copy);
          if (!continueIteration) {
            lastVisitedIndex.set(new DeliveryDeadlineIndex(deadline, attemptKey));
          }
          return continueIteration;
        });

    return lastVisitedIndex.get();
  }
}
