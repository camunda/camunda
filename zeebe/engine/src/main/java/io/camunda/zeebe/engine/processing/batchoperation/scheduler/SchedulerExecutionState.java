/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static com.google.common.base.Strings.emptyToNull;

import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import java.util.Objects;

/**
 * Tracks the in-flight state of a batch operation being initialized.
 *
 * <p>This class exists to prevent re-initialization of a batch operation before the stream
 * processor has processed our previous INITIALIZE command. Without this guard, the scheduler could
 * fire multiple INITIALIZE commands for the same cursor position, causing duplicate work.
 *
 * <p>The key invariant: after we successfully initialize a page and append an INITIALIZE command,
 * the persisted state's cursor and/or page size will be updated asynchronously by the command
 * processor. Until that happens, our in-memory state differs from the persisted state. We use this
 * difference to detect "command in flight" and skip the current scheduler tick — regardless of
 * whether a retry is pending. This provides natural back-pressure: if the stream processor is busy,
 * batch operations (which are lower priority) wait rather than adding more commands to the
 * pipeline.
 *
 * <p>Because we only proceed when persisted state has caught up, the scheduler can always use
 * persisted values directly via {@link InitializationContext#fromBatchOperation} — no in-memory
 * overrides are needed.
 *
 * @param defaultPageSize stored at creation to resolve persisted page size (which uses {@code -1}
 *     as sentinel for "unset") in {@link #shouldSkipInitialization} comparisons
 * @param pageSize the resolved page size; eagerly set from persisted state at creation, then
 *     updated by {@link #withReducedPageSize} when buffer-full scenarios occur
 */
record SchedulerExecutionState(
    long batchOperationKey, String cursor, int defaultPageSize, int pageSize, int numAttempts) {

  /** Creates initial state from a persisted batch operation. */
  static SchedulerExecutionState from(
      final PersistedBatchOperation batchOperation, final int defaultPageSize) {
    return new SchedulerExecutionState(
        batchOperation.getKey(),
        emptyToNull(batchOperation.getInitializationSearchCursor()),
        defaultPageSize,
        batchOperation.getInitializationSearchQueryPageSize(defaultPageSize),
        0);
  }

  /**
   * Advances to a new cursor after successful page processing. Resets retry count but preserves
   * page size.
   */
  SchedulerExecutionState advanceTo(final String newCursor) {
    return new SchedulerExecutionState(batchOperationKey, newCursor, defaultPageSize, pageSize, 0);
  }

  /** Prepares for a retry attempt with the given cursor and attempt count. Preserves page size. */
  SchedulerExecutionState retryWith(final String newCursor, final int attempts) {
    return new SchedulerExecutionState(
        batchOperationKey, newCursor, defaultPageSize, pageSize, attempts);
  }

  /** Reduces page size for next attempt due to buffer full. */
  SchedulerExecutionState withReducedPageSize(final int newPageSize) {
    return new SchedulerExecutionState(
        batchOperationKey, cursor, defaultPageSize, newPageSize, numAttempts);
  }

  /**
   * Determines whether initialization should be skipped for the given batch operation.
   *
   * <p>We skip in three cases:
   *
   * <ul>
   *   <li><b>Suspended:</b> the batch operation has been suspended and should not be processed
   *       until resumed
   *   <li><b>Cursor differs:</b> a page was successfully initialized and a continueInitialization
   *       command was written, but hasn't been processed yet by the stream processor
   *   <li><b>Page size differs:</b> a buffer-full scenario triggered a page size reduction and a
   *       continueInitialization command was written with the new size, but hasn't been processed
   *       yet
   * </ul>
   *
   * <p>The last two cases provide natural back-pressure: batch operations are lower priority than
   * normal record processing, so we wait rather than adding more commands to a busy pipeline. This
   * applies equally to retries — if a retry wrote a {@code continueInitialization} command, we wait
   * for it to be processed before scheduling more work.
   */
  boolean shouldSkipInitialization(final PersistedBatchOperation batchOperation) {
    if (batchOperationKey != batchOperation.getKey()) {
      return false;
    }
    if (batchOperation.isSuspended()) {
      return true;
    }
    if (!Objects.equals(cursor, emptyToNull(batchOperation.getInitializationSearchCursor()))) {
      return true;
    }
    return pageSize != batchOperation.getInitializationSearchQueryPageSize(defaultPageSize);
  }
}
