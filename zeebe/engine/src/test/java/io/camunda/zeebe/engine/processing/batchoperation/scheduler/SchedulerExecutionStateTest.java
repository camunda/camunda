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
            .setInitializationSearchCursor("cursor1")
            .setInitializationSearchQueryPageSize(75);

    // when
    final var state = SchedulerExecutionState.from(batchOperation, DEFAULT_PAGE_SIZE);

    // then
    assertThat(state.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(state.cursor()).isEqualTo("cursor1");
    assertThat(state.defaultPageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
    assertThat(state.pageSize()).isEqualTo(75);
    assertThat(state.numAttempts()).isZero();
  }

  @Test
  void shouldCreateFromBatchOperationWithDefaultPageSize() {
    // given - page size not set (-1), should resolve to default
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("cursor1");

    // when
    final var state = SchedulerExecutionState.from(batchOperation, DEFAULT_PAGE_SIZE);

    // then
    assertThat(state.pageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
  }

  @Test
  void shouldAdvanceToCursor() {
    // given
    final var state =
        new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor1", DEFAULT_PAGE_SIZE, 50, 2);

    // when
    final var advanced = state.advanceTo("cursor2");

    // then - cursor updated, numAttempts reset, pageSize and defaultPageSize preserved
    assertThat(advanced.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(advanced.cursor()).isEqualTo("cursor2");
    assertThat(advanced.defaultPageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
    assertThat(advanced.pageSize()).isEqualTo(50);
    assertThat(advanced.numAttempts()).isZero();
  }

  @Test
  void shouldRetryWithCursorAndAttempts() {
    // given
    final var state =
        new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor1", DEFAULT_PAGE_SIZE, 50, 0);

    // when
    final var retrying = state.retryWith("cursor2", 3);

    // then - cursor and attempts updated, pageSize and defaultPageSize preserved
    assertThat(retrying.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(retrying.cursor()).isEqualTo("cursor2");
    assertThat(retrying.defaultPageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
    assertThat(retrying.pageSize()).isEqualTo(50);
    assertThat(retrying.numAttempts()).isEqualTo(3);
  }

  @Test
  void shouldReducePageSize() {
    // given
    final var state =
        new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor1", DEFAULT_PAGE_SIZE, 100, 1);

    // when
    final var reduced = state.withReducedPageSize(50);

    // then - pageSize updated, cursor, defaultPageSize, and numAttempts preserved
    assertThat(reduced.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(reduced.cursor()).isEqualTo("cursor1");
    assertThat(reduced.defaultPageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
    assertThat(reduced.pageSize()).isEqualTo(50);
    assertThat(reduced.numAttempts()).isEqualTo(1);
  }

  @Test
  void shouldSkipWhenCursorsDiffer() {
    // given - in-memory cursor differs from persisted (command in-flight)
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("persisted-cursor");
    final var state =
        new SchedulerExecutionState(
            BATCH_OPERATION_KEY, "in-memory-cursor", DEFAULT_PAGE_SIZE, 100, 0);

    // when / then
    assertThat(state.shouldSkipInitialization(batchOperation)).isTrue();
  }

  @Test
  void shouldNotSkipWhenCursorsMatch() {
    // given - cursors match (persisted state caught up)
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("same-cursor");
    final var state =
        new SchedulerExecutionState(BATCH_OPERATION_KEY, "same-cursor", DEFAULT_PAGE_SIZE, 100, 0);

    // when / then
    assertThat(state.shouldSkipInitialization(batchOperation)).isFalse();
  }

  @Test
  void shouldSkipWhenRetryInProgressAndCommandInFlight() {
    // given - cursors differ AND retry in progress (numAttempts > 0)
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("persisted-cursor");
    final var state =
        new SchedulerExecutionState(
            BATCH_OPERATION_KEY, "in-memory-cursor", DEFAULT_PAGE_SIZE, 100, 1);

    // when / then - skip because command is in-flight, even during retries
    assertThat(state.shouldSkipInitialization(batchOperation)).isTrue();
  }

  @Test
  void shouldNotSkipRetryWhenCursorsMatch() {
    // given - retry in progress but cursors match (no command was written)
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("same-cursor");
    final var state =
        new SchedulerExecutionState(BATCH_OPERATION_KEY, "same-cursor", DEFAULT_PAGE_SIZE, 100, 2);

    // when / then - cursors match means no command in-flight, retry can proceed
    assertThat(state.shouldSkipInitialization(batchOperation)).isFalse();
  }

  @Test
  void shouldNotSkipWhenDifferentBatchOperationKey() {
    // given - different batch operation
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(999L)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("cursor");
    final var state =
        new SchedulerExecutionState(
            BATCH_OPERATION_KEY, "different-cursor", DEFAULT_PAGE_SIZE, 100, 0);

    // when / then
    assertThat(state.shouldSkipInitialization(batchOperation)).isFalse();
  }

  @Test
  void shouldHandleEmptyCursorsInShouldSkipInitialization() {
    // given - both cursors are empty; from() normalizes "" to null via emptyToNull
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("");
    final var state =
        new SchedulerExecutionState(BATCH_OPERATION_KEY, null, DEFAULT_PAGE_SIZE, 100, 0);

    // when / then - both normalize to null, so they match
    assertThat(state.shouldSkipInitialization(batchOperation)).isFalse();
  }

  @Test
  void shouldSkipWhenSuspended() {
    // given - batch operation is suspended
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.SUSPENDED)
            .setInitializationSearchCursor("cursor");
    final var state =
        new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor", DEFAULT_PAGE_SIZE, 100, 0);

    // when / then - skip because operation is suspended
    assertThat(state.shouldSkipInitialization(batchOperation)).isTrue();
  }

  @Test
  void shouldSkipWhenPageSizeDiffers() {
    // given - cursor matches but in-memory page size differs from persisted
    // (continueInitialization command with reduced page size not yet processed)
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("cursor")
            .setInitializationSearchQueryPageSize(100);
    final var state =
        new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor", DEFAULT_PAGE_SIZE, 50, 0);

    // when / then
    assertThat(state.shouldSkipInitialization(batchOperation)).isTrue();
  }

  @Test
  void shouldSkipWhenPageSizeDiffersAndPersistedIsUnset() {
    // given - persisted page size is unset (-1), resolves to defaultPageSize (100)
    // but in-memory was reduced to 50 — command not yet processed
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("cursor");
    // persisted raw is -1 (default), resolves to DEFAULT_PAGE_SIZE=100
    final var state =
        new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor", DEFAULT_PAGE_SIZE, 50, 0);

    // when / then - 50 != 100, skip
    assertThat(state.shouldSkipInitialization(batchOperation)).isTrue();
  }

  @Test
  void shouldNotSkipWhenPageSizeMatchesPersisted() {
    // given - cursor matches and in-memory page size matches persisted
    // (continueInitialization command has been processed)
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("cursor")
            .setInitializationSearchQueryPageSize(50);
    final var state =
        new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor", DEFAULT_PAGE_SIZE, 50, 0);

    // when / then
    assertThat(state.shouldSkipInitialization(batchOperation)).isFalse();
  }

  @Test
  void shouldNotSkipWhenPageSizesMatchFromDefault() {
    // given - both in-memory and persisted resolve to the same page size
    final var batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setStatus(BatchOperationStatus.CREATED)
            .setInitializationSearchCursor("cursor")
            .setInitializationSearchQueryPageSize(100);
    final var state =
        new SchedulerExecutionState(BATCH_OPERATION_KEY, "cursor", DEFAULT_PAGE_SIZE, 100, 0);

    // when / then - no override pending, page sizes agree
    assertThat(state.shouldSkipInitialization(batchOperation)).isFalse();
  }
}
