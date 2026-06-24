/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus.CREATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProviderFactory;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationChunkAppender.ChunkingOutcome;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BatchOperationInitializationBehaviorTest {

  private static final long BATCH_OPERATION_KEY = 12345L;
  private static final String SEARCH_CURSOR = "test-cursor";
  private static final String NEXT_SEARCH_CURSOR = "next-cursor";
  private static final int PAGE_SIZE = 100;

  private final ItemProviderFactory itemProviderFactory = mock(ItemProviderFactory.class);
  private final BatchOperationChunkAppender chunkAppender = mock(BatchOperationChunkAppender.class);
  private final BatchOperationCommands commandBuilder = mock(BatchOperationCommands.class);
  private final BatchOperationMetrics metrics = mock(BatchOperationMetrics.class);
  private final TaskResultBuilder taskResultBuilder = mock(TaskResultBuilder.class);
  private PersistedBatchOperation batchOperation;

  private BatchOperationInitializationBehavior initializer;

  @BeforeEach
  void setUp() {
    initializer =
        new BatchOperationInitializationBehavior(
            itemProviderFactory, chunkAppender, commandBuilder, metrics);

    // Default batch operation setup
    batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setInitializationSearchCursor(SEARCH_CURSOR)
            .setInitializationSearchQueryPageSize(PAGE_SIZE)
            .setNumTotalItems(0)
            .setStatus(CREATED);
  }

  /** Creates an InitializationContext from the batch operation for testing. */
  private InitializationContext createContext() {
    return new InitializationContext(batchOperation, SEARCH_CURSOR, PAGE_SIZE, 0, false);
  }

  @Test
  void shouldInitializeSuccessfullyWithSinglePageAndFinish() {
    // given
    when(chunkAppender.fetchAndChunkNextPage(any(), any(), eq(taskResultBuilder)))
        .thenReturn(new ChunkingOutcome.Finished(NEXT_SEARCH_CURSOR, 2));

    // when
    final var result = initializer.initializeBatchOperation(createContext(), taskResultBuilder);

    // then
    assertThat(result).isInstanceOf(InitializationOutcome.Success.class);
    final var success = (InitializationOutcome.Success) result;
    assertThat(success.cursor()).isEqualTo(NEXT_SEARCH_CURSOR);

    verify(commandBuilder)
        .appendFinishInitializationCommand(taskResultBuilder, BATCH_OPERATION_KEY);
    verify(commandBuilder).appendExecutionCommand(taskResultBuilder, BATCH_OPERATION_KEY);
    verify(metrics).recordItemsPerPartition(2, BatchOperationType.CANCEL_PROCESS_INSTANCE);
    verify(metrics)
        .startStartExecuteLatencyMeasure(
            BATCH_OPERATION_KEY, BatchOperationType.CANCEL_PROCESS_INSTANCE);
    verify(metrics)
        .startTotalExecutionLatencyMeasure(
            BATCH_OPERATION_KEY, BatchOperationType.CANCEL_PROCESS_INSTANCE);
  }

  @Test
  void shouldHandleMultiplePagesSuccessfully() {
    // given
    when(chunkAppender.fetchAndChunkNextPage(any(), any(), eq(taskResultBuilder)))
        .thenReturn(new ChunkingOutcome.Continue("cursor1", 2))
        .thenReturn(new ChunkingOutcome.Finished("cursor2", 2));

    // when
    final var result = initializer.initializeBatchOperation(createContext(), taskResultBuilder);

    // then
    assertThat(result).isInstanceOf(InitializationOutcome.Success.class);
    final var success = (InitializationOutcome.Success) result;
    assertThat(success.cursor()).isEqualTo("cursor2");

    verify(commandBuilder)
        .appendFinishInitializationCommand(taskResultBuilder, BATCH_OPERATION_KEY);
    verify(commandBuilder).appendExecutionCommand(taskResultBuilder, BATCH_OPERATION_KEY);
    verify(metrics).recordItemsPerPartition(4, BatchOperationType.CANCEL_PROCESS_INSTANCE);
  }

  @Test
  void shouldHandleFailedChunkAppendWithoutPreviousChunks() {
    // given
    when(chunkAppender.fetchAndChunkNextPage(any(), any(), eq(taskResultBuilder)))
        .thenReturn(new ChunkingOutcome.BufferFull());

    // when
    final var result = initializer.initializeBatchOperation(createContext(), taskResultBuilder);

    // then
    assertThat(result).isInstanceOf(InitializationOutcome.ReducePageSize.class);
    final var reducePageSize = (InitializationOutcome.ReducePageSize) result;
    assertThat(reducePageSize.newPageSize()).isEqualTo(PAGE_SIZE / 2);

    verify(commandBuilder)
        .appendInitializationCommand(
            eq(taskResultBuilder),
            eq(BATCH_OPERATION_KEY),
            anyString(),
            eq(PAGE_SIZE / 2)); // Should write INITIALIZE with reduced page size
  }

  @Test
  void shouldHandleFailedChunkAppendWithPreviousChunks() {
    // given - First page succeeds, second page fails to fit in buffer
    when(chunkAppender.fetchAndChunkNextPage(any(), any(), eq(taskResultBuilder)))
        .thenReturn(new ChunkingOutcome.Continue("cursor1", 2))
        .thenReturn(new ChunkingOutcome.BufferFull());

    // when
    final var result = initializer.initializeBatchOperation(createContext(), taskResultBuilder);

    // then - returns Success (meaning "progress made, continue later") with cursor at last
    // successful page
    assertThat(result).isInstanceOf(InitializationOutcome.Success.class);
    final var success = (InitializationOutcome.Success) result;
    assertThat(success.cursor()).isEqualTo("cursor1");

    // INITIALIZE command written to persist progress
    verify(commandBuilder)
        .appendInitializationCommand(
            eq(taskResultBuilder), eq(BATCH_OPERATION_KEY), eq("cursor1"), eq(PAGE_SIZE));
  }

  @Test
  void shouldReturnNeedsRetryWhenFetchFailsWithoutPreviousChunks() {
    // given
    final var exception = new RuntimeException("Database connection failed");
    when(chunkAppender.fetchAndChunkNextPage(any(), any(), eq(taskResultBuilder)))
        .thenReturn(new ChunkingOutcome.FetchFailed(exception));

    // when
    final var result = initializer.initializeBatchOperation(createContext(), taskResultBuilder);

    // then
    assertThat(result).isInstanceOf(InitializationOutcome.NeedsRetry.class);
    final var needsRetry = (InitializationOutcome.NeedsRetry) result;
    assertThat(needsRetry.cursor()).isEqualTo(SEARCH_CURSOR);
    assertThat(needsRetry.cause()).isSameAs(exception);

    verify(commandBuilder, never())
        .appendInitializationCommand(any(), anyLong(), anyString(), anyInt());
  }

  @Test
  void shouldContinueInitializationWhenFetchFailsWithPreviousChunks() {
    // given - First page succeeds, second page fetch fails
    final var exception = new RuntimeException("Database connection failed");
    when(chunkAppender.fetchAndChunkNextPage(any(), any(), eq(taskResultBuilder)))
        .thenReturn(new ChunkingOutcome.Continue("cursor1", 2))
        .thenReturn(new ChunkingOutcome.FetchFailed(exception));

    // when
    final var result = initializer.initializeBatchOperation(createContext(), taskResultBuilder);

    // then
    assertThat(result).isInstanceOf(InitializationOutcome.NeedsRetry.class);
    final var needsRetry = (InitializationOutcome.NeedsRetry) result;
    assertThat(needsRetry.cursor()).isEqualTo("cursor1");
    assertThat(needsRetry.cause()).isSameAs(exception);

    verify(commandBuilder)
        .appendInitializationCommand(
            eq(taskResultBuilder), eq(BATCH_OPERATION_KEY), eq("cursor1"), anyInt());
  }

  @Test
  void shouldAppendFailedCommandUsingPublicMethod() {
    // given
    final var errorMessage = "Test error message";
    final var errorType = BatchOperationErrorType.QUERY_FAILED;

    // when
    initializer.appendFailedCommand(
        taskResultBuilder, batchOperation.getKey(), errorMessage, errorType);

    // then
    verify(commandBuilder)
        .appendFailureCommand(taskResultBuilder, batchOperation.getKey(), errorMessage, errorType);
  }

  @Test
  void shouldHandleEmptyPageSuccessfully() {
    // given
    when(chunkAppender.fetchAndChunkNextPage(any(), any(), eq(taskResultBuilder)))
        .thenReturn(new ChunkingOutcome.Finished(NEXT_SEARCH_CURSOR, 0));

    // when
    final var result = initializer.initializeBatchOperation(createContext(), taskResultBuilder);

    // then
    assertThat(result).isInstanceOf(InitializationOutcome.Success.class);
    final var success = (InitializationOutcome.Success) result;
    assertThat(success.cursor()).isEqualTo(NEXT_SEARCH_CURSOR);

    verify(commandBuilder)
        .appendFinishInitializationCommand(taskResultBuilder, BATCH_OPERATION_KEY);
    verify(commandBuilder).appendExecutionCommand(taskResultBuilder, BATCH_OPERATION_KEY);
    verify(metrics).recordItemsPerPartition(0, BatchOperationType.CANCEL_PROCESS_INSTANCE);
  }

  @Test
  void shouldUpdateTotalItemsCountCorrectly() {
    // given
    batchOperation.setNumTotalItems(5);
    when(chunkAppender.fetchAndChunkNextPage(any(), any(), eq(taskResultBuilder)))
        .thenReturn(new ChunkingOutcome.Finished(NEXT_SEARCH_CURSOR, 3));

    // when
    initializer.initializeBatchOperation(createContext(), taskResultBuilder);

    // then
    verify(metrics).recordItemsPerPartition(8, BatchOperationType.CANCEL_PROCESS_INSTANCE); // 5 + 3
  }

  @Test
  void shouldInitializeDeleteProcessInstanceBatchOperation() {
    // given
    batchOperation.setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE);
    when(chunkAppender.fetchAndChunkNextPage(any(), any(), eq(taskResultBuilder)))
        .thenReturn(new ChunkingOutcome.Finished(NEXT_SEARCH_CURSOR, 2));

    // when
    final var result = initializer.initializeBatchOperation(createContext(), taskResultBuilder);

    // then
    assertThat(result).isInstanceOf(InitializationOutcome.Success.class);
    final var success = (InitializationOutcome.Success) result;
    assertThat(success.cursor()).isEqualTo(NEXT_SEARCH_CURSOR);
    verify(commandBuilder)
        .appendFinishInitializationCommand(taskResultBuilder, BATCH_OPERATION_KEY);
    verify(commandBuilder).appendExecutionCommand(taskResultBuilder, BATCH_OPERATION_KEY);
    verify(metrics).recordItemsPerPartition(2, BatchOperationType.DELETE_PROCESS_INSTANCE);
    verify(metrics)
        .startStartExecuteLatencyMeasure(
            BATCH_OPERATION_KEY, BatchOperationType.DELETE_PROCESS_INSTANCE);
    verify(metrics)
        .startTotalExecutionLatencyMeasure(
            BATCH_OPERATION_KEY, BatchOperationType.DELETE_PROCESS_INSTANCE);
  }

  @Test
  void shouldFailWhenBufferFullAndPageSizeAlreadyMinimum() {
    // given - page size is already 1, cannot reduce further
    final var minPageSizeContext =
        new InitializationContext(batchOperation, SEARCH_CURSOR, 1, 0, false);

    when(chunkAppender.fetchAndChunkNextPage(any(), any(), eq(taskResultBuilder)))
        .thenReturn(new ChunkingOutcome.BufferFull());

    // when
    final var result = initializer.initializeBatchOperation(minPageSizeContext, taskResultBuilder);

    // then - terminal failure since we can't reduce page size below 1
    assertThat(result).isInstanceOf(InitializationOutcome.Failed.class);
    final var failed = (InitializationOutcome.Failed) result;
    assertThat(failed.message()).contains("Result buffer too small");
    assertThat(failed.errorType()).isEqualTo(BatchOperationErrorType.RESULT_BUFFER_SIZE_EXCEEDED);

    // No INITIALIZE command should be written for terminal failure
    verify(commandBuilder, never())
        .appendInitializationCommand(any(), anyLong(), anyString(), anyInt());
  }
}
