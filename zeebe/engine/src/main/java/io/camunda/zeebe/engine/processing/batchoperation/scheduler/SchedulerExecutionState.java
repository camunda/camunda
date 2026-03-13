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
 * the persisted state's cursor will be updated asynchronously by the command processor. Until that
 * happens, our in-memory cursor differs from the persisted cursor. We use this difference to detect
 * "command in flight" and skip re-initialization.
 *
 * <p>However, if retries are in flight ({@code numAttempts > 0}), we should NOT skip — retries are
 * intentional re-executions after transient failures.
 *
 * <p>The state also tracks a reduced page size when buffer-full scenarios require smaller fetches.
 * This avoids writing INITIALIZE commands just to persist the page size change.
 *
 * @param pageSize 0 means use default
 */
record SchedulerExecutionState(
    long batchOperationKey, String cursor, int pageSize, int numAttempts) {

  /** Creates initial state from a persisted batch operation. */
  static SchedulerExecutionState from(final PersistedBatchOperation batchOperation) {
    return new SchedulerExecutionState(
        batchOperation.getKey(), batchOperation.getInitializationSearchCursor(), 0, 0);
  }

  /**
   * Advances to a new cursor after successful page processing. Resets page size and retry count.
   */
  SchedulerExecutionState advanceTo(final String newCursor) {
    return new SchedulerExecutionState(batchOperationKey, newCursor, 0, 0);
  }

  /** Prepares for a retry attempt with the given cursor and attempt count. Preserves page size. */
  SchedulerExecutionState retryWith(final String newCursor, final int attempts) {
    return new SchedulerExecutionState(batchOperationKey, newCursor, pageSize, attempts);
  }

  /** Reduces page size for next attempt due to buffer full. */
  SchedulerExecutionState withReducedPageSize(final int newPageSize) {
    return new SchedulerExecutionState(batchOperationKey, cursor, newPageSize, numAttempts);
  }

  /**
   * Builds an {@link InitializationContext} using in-memory overrides where applicable.
   *
   * <p>Uses:
   *
   * <ul>
   *   <li>In-memory cursor when retrying ({@code numAttempts > 0}) to avoid stale cursor race
   *   <li>In-memory page size when reduced ({@code pageSize > 0}) to avoid stale page size race
   *   <li>Persisted values otherwise
   * </ul>
   */
  InitializationContext buildContext(
      final PersistedBatchOperation batchOperation, final int defaultPageSize) {
    final String effectiveCursor =
        emptyToNull(numAttempts > 0 ? cursor : batchOperation.getInitializationSearchCursor());
    final int effectivePageSize =
        pageSize > 0
            ? pageSize
            : batchOperation.getInitializationSearchQueryPageSize(defaultPageSize);
    return new InitializationContext(batchOperation, effectiveCursor, effectivePageSize, 0, false);
  }

  /**
   * Determines whether initialization should be skipped for the given batch operation.
   *
   * <p>We skip when our in-memory cursor differs from the persisted cursor AND we're not in a
   * retry. This means we appended an INITIALIZE command that hasn't been processed yet.
   */
  boolean shouldSkipInitialization(final PersistedBatchOperation batchOperation) {
    return batchOperationKey == batchOperation.getKey()
        && numAttempts == 0
        && !Objects.equals(cursor, batchOperation.getInitializationSearchCursor());
  }
}
