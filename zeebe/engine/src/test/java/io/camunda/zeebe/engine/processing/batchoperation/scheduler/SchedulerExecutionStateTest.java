/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus;
import org.junit.jupiter.api.Test;

class SchedulerExecutionStateTest {

  private static final long BATCH_OPERATION_KEY = 123L;
  private static final int DEFAULT_PAGE_SIZE = 100;

  @Test
  void shouldCreateFromBatchOperation() {
    // given
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("cursor1");

    // when
    final var state = SchedulerExecutionState.from(batchOperation);

    // then
    assertThat(state.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(state.cursor()).isEqualTo("cursor1");
    assertThat(state.pageSize()).isZero();
    assertThat(state.numAttempts()).isZero();
  }

  @Test
  void shouldAdvanceToCursor() {
    // given
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor1", 50, 2);

    // when
    final var advanced = state.advanceTo("cursor2");

    // then - cursor updated, pageSize and numAttempts reset
    assertThat(advanced.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(advanced.cursor()).isEqualTo("cursor2");
    assertThat(advanced.pageSize()).isZero();
    assertThat(advanced.numAttempts()).isZero();
  }

  @Test
  void shouldRetryWithCursorAndAttempts() {
    // given
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor1", 50, 0);

    // when
    final var retrying = state.retryWith("cursor2", 3);

    // then - cursor and attempts updated, pageSize preserved
    assertThat(retrying.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(retrying.cursor()).isEqualTo("cursor2");
    assertThat(retrying.pageSize()).isEqualTo(50);
    assertThat(retrying.numAttempts()).isEqualTo(3);
  }

  @Test
  void shouldReducePageSize() {
    // given
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor1", 100, 1);

    // when
    final var reduced = state.withReducedPageSize(50);

    // then - pageSize updated, cursor and numAttempts preserved
    assertThat(reduced.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(reduced.cursor()).isEqualTo("cursor1");
    assertThat(reduced.pageSize()).isEqualTo(50);
    assertThat(reduced.numAttempts()).isEqualTo(1);
  }

  @Test
  void shouldBuildContextWithPersistedValuesWhenNoOverrides() {
    // given
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("persisted-cursor")
            .setInitializationSearchQueryPageSize(75);
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "persisted-cursor", 0, 0);

    // when
    final var context = state.buildContext(batchOperation, DEFAULT_PAGE_SIZE);

    // then - uses persisted values
    assertThat(context.currentCursor()).isEqualTo("persisted-cursor");
    assertThat(context.pageSize()).isEqualTo(75);
  }

  @Test
  void shouldBuildContextWithInMemoryCursorWhenRetrying() {
    // given - numAttempts > 0 indicates retry in progress
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("old-persisted-cursor")
            .setInitializationSearchQueryPageSize(75);
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "in-memory-cursor", 0, 1);

    // when
    final var context = state.buildContext(batchOperation, DEFAULT_PAGE_SIZE);

    // then - uses in-memory cursor (not persisted) to avoid stale cursor
    assertThat(context.currentCursor()).isEqualTo("in-memory-cursor");
    assertThat(context.pageSize()).isEqualTo(75);
  }

  @Test
  void shouldBuildContextWithInMemoryPageSizeWhenReduced() {
    // given - pageSize > 0 indicates reduced page size
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("cursor")
            .setInitializationSearchQueryPageSize(100);
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor", 50, 0);

    // when
    final var context = state.buildContext(batchOperation, DEFAULT_PAGE_SIZE);

    // then - uses in-memory page size (not persisted)
    assertThat(context.currentCursor()).isEqualTo("cursor");
    assertThat(context.pageSize()).isEqualTo(50);
  }

  @Test
  void shouldBuildContextWithDefaultPageSizeWhenNotSet() {
    // given - batch operation has no page size set
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("cursor");
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor", 0, 0);

    // when
    final var context = state.buildContext(batchOperation, DEFAULT_PAGE_SIZE);

    // then - uses default page size
    assertThat(context.pageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
  }

  @Test
  void shouldSkipWhenCursorsDifferAndNoRetryInProgress() {
    // given - in-memory cursor differs from persisted (command in-flight)
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("persisted-cursor");
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "in-memory-cursor", 0, 0);

    // when
    final var shouldSkip = state.shouldSkipInitialization(batchOperation);

    // then - skip because command is in-flight
    assertThat(shouldSkip).isTrue();
  }

  @Test
  void shouldNotSkipWhenCursorsMatch() {
    // given - cursors match (persisted state caught up)
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("same-cursor");
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "same-cursor", 0, 0);

    // when
    final var shouldSkip = state.shouldSkipInitialization(batchOperation);

    // then - don't skip, we can continue processing
    assertThat(shouldSkip).isFalse();
  }

  @Test
  void shouldNotSkipWhenRetryInProgress() {
    // given - cursors differ BUT retry in progress (numAttempts > 0)
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("persisted-cursor");
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "in-memory-cursor", 0, 1);

    // when
    final var shouldSkip = state.shouldSkipInitialization(batchOperation);

    // then - don't skip, retries are intentional
    assertThat(shouldSkip).isFalse();
  }

  @Test
  void shouldNotSkipWhenDifferentBatchOperationKey() {
    // given - different batch operation
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(999L) // Different key
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("cursor");
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "different-cursor", 0, 0);

    // when
    final var shouldSkip = state.shouldSkipInitialization(batchOperation);

    // then - don't skip, it's a different batch operation
    assertThat(shouldSkip).isFalse();
  }

  @Test
  void shouldHandleNullCursorsInShouldSkipInitialization() {
    // given - both cursors are null/empty
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("");
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "", 0, 0);

    // when
    final var shouldSkip = state.shouldSkipInitialization(batchOperation);

    // then - don't skip, cursors match (both empty)
    assertThat(shouldSkip).isFalse();
  }

  @Test
  void shouldConvertEmptyCursorToNullInBuildContext() {
    // given - empty cursor in persisted state
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("")
            .setInitializationSearchQueryPageSize(50);
    final var state = new SchedulerExecutionState(BATCH_OPERATION_KEY, "", 0, 0);

    // when
    final var context = state.buildContext(batchOperation, DEFAULT_PAGE_SIZE);

    // then - empty string converted to null
    assertThat(context.currentCursor()).isNull();
  }
}
