/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus.CREATED;
import static io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus.SUSPENDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.Item;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.ItemPage;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProviderFactory;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationHelper.BatchOperationInitializationException;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationPageProcessor.PageProcessingResult;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BatchOperationInitializationHelperTest {

  private static final long BATCH_OPERATION_KEY = 12345L;
  private static final String SEARCH_CURSOR = "test-cursor";
  private static final String NEXT_SEARCH_CURSOR = "next-cursor";
  private static final int PAGE_SIZE = 100;

  private final ItemProviderFactory itemProviderFactory = mock(ItemProviderFactory.class);
  private final ItemProvider itemProvider = mock(ItemProvider.class);
  private final BatchOperationPageProcessor pageProcessor = mock(BatchOperationPageProcessor.class);
  private final BatchOperationCommandAppender commandBuilder =
      mock(BatchOperationCommandAppender.class);
  private final BatchOperationMetrics metrics = mock(BatchOperationMetrics.class);
  private final TaskResultBuilder taskResultBuilder = mock(TaskResultBuilder.class);
  private PersistedBatchOperation batchOperation;

  private BatchOperationInitializationHelper initializer;

  @BeforeEach
  void setUp() {
    initializer =
        new BatchOperationInitializationHelper(
            itemProviderFactory, pageProcessor, commandBuilder, PAGE_SIZE, metrics);

    // Default batch operation setup
    batchOperation =
        new PersistedBatchOperation()
            .setKey(BATCH_OPERATION_KEY)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setInitializationSearchCursor(SEARCH_CURSOR)
            .setInitializationSearchQueryPageSize(PAGE_SIZE)
            .setNumTotalItems(0)
            .setStatus(CREATED);

    when(itemProviderFactory.fromBatchOperation(batchOperation)).thenReturn(itemProvider);
  }

  @Test
  void shouldReturnEarlyWhenBatchOperationIsSuspended() {
    // given
    batchOperation.setStatus(SUSPENDED);

    // when
    final var result = initializer.initializeBatchOperation(batchOperation, taskResultBuilder);

    // then
    assertThat(result.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(result.searchResultCursor()).isEqualTo(SEARCH_CURSOR);
    verify(itemProvider, never()).fetchItemPage(anyString(), any(Integer.class));
    verify(pageProcessor, never()).processPage(anyLong(), any(), any());
  }

  @Test
  void shouldInitializeSuccessfullyWithSinglePageAndFinish() {
    // given
    final var items = List.of(createItem(1L), createItem(2L));
    final var itemPage = new ItemPage(items, NEXT_SEARCH_CURSOR, 2L, true);
    final var pageResult = new PageProcessingResult(true, NEXT_SEARCH_CURSOR, 2, true);

    when(itemProvider.fetchItemPage(SEARCH_CURSOR, PAGE_SIZE)).thenReturn(itemPage);
    when(pageProcessor.processPage(batchOperation.getKey(), itemPage, taskResultBuilder))
        .thenReturn(pageResult);

    // when
    final var result = initializer.initializeBatchOperation(batchOperation, taskResultBuilder);

    // then
    assertThat(result.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(result.searchResultCursor()).isEqualTo("finished");

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
    final var firstPageItems = List.of(createItem(1L), createItem(2L));
    final var firstPage = new ItemPage(firstPageItems, "cursor1", 4L, false);
    final var firstPageResult = new PageProcessingResult(true, "cursor1", 2, false);

    final var secondPageItems = List.of(createItem(3L), createItem(4L));
    final var secondPage = new ItemPage(secondPageItems, "cursor2", 4L, true);
    final var secondPageResult = new PageProcessingResult(true, "cursor2", 2, true);

    when(itemProvider.fetchItemPage(SEARCH_CURSOR, PAGE_SIZE)).thenReturn(firstPage);
    when(itemProvider.fetchItemPage("cursor1", PAGE_SIZE)).thenReturn(secondPage);
    when(pageProcessor.processPage(batchOperation.getKey(), firstPage, taskResultBuilder))
        .thenReturn(firstPageResult);
    when(pageProcessor.processPage(batchOperation.getKey(), secondPage, taskResultBuilder))
        .thenReturn(secondPageResult);

    // when
    final var result = initializer.initializeBatchOperation(batchOperation, taskResultBuilder);

    // then
    assertThat(result.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(result.searchResultCursor()).isEqualTo("finished");

    verify(commandBuilder)
        .appendFinishInitializationCommand(taskResultBuilder, BATCH_OPERATION_KEY);
    verify(commandBuilder).appendExecutionCommand(taskResultBuilder, BATCH_OPERATION_KEY);
    verify(metrics).recordItemsPerPartition(4, BatchOperationType.CANCEL_PROCESS_INSTANCE);
  }

  @Test
  void shouldHandleFailedChunkAppendWithoutPreviousChunks() {
    // given
    final var items = List.of(createItem(1L), createItem(2L));
    final var itemPage = new ItemPage(items, NEXT_SEARCH_CURSOR, 2L, false);
    final var pageResult = new PageProcessingResult(false, NEXT_SEARCH_CURSOR, 2, false);

    when(itemProvider.fetchItemPage(SEARCH_CURSOR, PAGE_SIZE)).thenReturn(itemPage);
    when(pageProcessor.processPage(batchOperation.getKey(), itemPage, taskResultBuilder))
        .thenReturn(pageResult);

    // when
    final var result = initializer.initializeBatchOperation(batchOperation, taskResultBuilder);

    // then
    assertThat(result.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(result.searchResultCursor()).isEqualTo(SEARCH_CURSOR);

    verify(commandBuilder)
        .appendInitializationCommand(
            eq(taskResultBuilder),
            eq(BATCH_OPERATION_KEY),
            anyString(),
            eq(PAGE_SIZE / 2)); // Should reduce page size
  }

  @Test
  void shouldHandleFailedChunkAppendWithPreviousChunks() {
    // given - First page succeeds, second page fails
    final var firstPageItems = List.of(createItem(1L), createItem(2L));
    final var firstPage = new ItemPage(firstPageItems, "cursor1", 4L, false);
    final var firstPageResult = new PageProcessingResult(true, "cursor1", 2, false);

    final var secondPageItems = List.of(createItem(3L), createItem(4L));
    final var secondPage = new ItemPage(secondPageItems, "cursor2", 4L, false);
    final var secondPageResult = new PageProcessingResult(false, "cursor2", 2, false);

    when(itemProvider.fetchItemPage(SEARCH_CURSOR, PAGE_SIZE)).thenReturn(firstPage);
    when(itemProvider.fetchItemPage("cursor1", PAGE_SIZE)).thenReturn(secondPage);
    when(pageProcessor.processPage(batchOperation.getKey(), firstPage, taskResultBuilder))
        .thenReturn(firstPageResult);
    when(pageProcessor.processPage(batchOperation.getKey(), secondPage, taskResultBuilder))
        .thenReturn(secondPageResult);

    // when
    final var result = initializer.initializeBatchOperation(batchOperation, taskResultBuilder);

    // then
    assertThat(result.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(result.searchResultCursor()).isEqualTo("cursor1");

    verify(commandBuilder)
        .appendInitializationCommand(
            eq(taskResultBuilder), eq(BATCH_OPERATION_KEY), eq("cursor1"), eq(PAGE_SIZE));
  }

  @Test
  void shouldThrowExceptionWhenItemProviderFailsWithoutPreviousChunks() {
    // given
    final var exception = new RuntimeException("Database connection failed");
    when(itemProvider.fetchItemPage(SEARCH_CURSOR, PAGE_SIZE)).thenThrow(exception);

    // when & then
    assertThatThrownBy(
            () -> initializer.initializeBatchOperation(batchOperation, taskResultBuilder))
        .isInstanceOf(BatchOperationInitializationException.class)
        .hasMessageContaining(
            "Failed to initialize batch operation with end cursor: " + SEARCH_CURSOR)
        .hasCause(exception);

    verify(commandBuilder, never())
        .appendInitializationCommand(any(), anyLong(), anyString(), anyInt());
  }

  @Test
  void shouldContinueInitializationWhenItemProviderFailsWithPreviousChunks() {
    // given - First page succeeds, second page throws exception
    final var firstPageItems = List.of(createItem(1L), createItem(2L));
    final var firstPage = new ItemPage(firstPageItems, "cursor1", 4L, false);
    final var firstPageResult = new PageProcessingResult(true, "cursor1", 2, false);

    final var exception = new RuntimeException("Database connection failed");

    when(itemProvider.fetchItemPage(SEARCH_CURSOR, PAGE_SIZE)).thenReturn(firstPage);
    when(itemProvider.fetchItemPage("cursor1", PAGE_SIZE)).thenThrow(exception);
    when(pageProcessor.processPage(batchOperation.getKey(), firstPage, taskResultBuilder))
        .thenReturn(firstPageResult);

    // when & then
    assertThatThrownBy(
            () -> initializer.initializeBatchOperation(batchOperation, taskResultBuilder))
        .isInstanceOf(BatchOperationInitializationException.class)
        .hasMessageContaining("Failed to initialize batch operation with end cursor: cursor1")
        .hasCause(exception);

    verify(commandBuilder)
        .appendInitializationCommand(
            eq(taskResultBuilder), eq(BATCH_OPERATION_KEY), eq("cursor1"), anyInt());
  }

  @Test
  void shouldAppendFailedCommandUsingPublicMethod() {
    // given
    final var errorMessage = "Test error message";
    final var errorType = BatchOperationErrorType.QUERY_FAILED;
    final var exception =
        new BatchOperationInitializationException(errorMessage, errorType, "test-cursor");

    // when
    initializer.appendFailedCommand(taskResultBuilder, batchOperation.getKey(), exception);

    // then
    verify(commandBuilder)
        .appendFailureCommand(taskResultBuilder, batchOperation.getKey(), errorMessage, errorType);
  }

  @Test
  void shouldHandleEmptyPageSuccessfully() {
    // given
    final var emptyPage = new ItemPage(List.of(), NEXT_SEARCH_CURSOR, 0L, true);
    final var pageResult = new PageProcessingResult(true, NEXT_SEARCH_CURSOR, 0, true);

    when(itemProvider.fetchItemPage(SEARCH_CURSOR, PAGE_SIZE)).thenReturn(emptyPage);
    when(pageProcessor.processPage(batchOperation.getKey(), emptyPage, taskResultBuilder))
        .thenReturn(pageResult);

    // when
    final var result = initializer.initializeBatchOperation(batchOperation, taskResultBuilder);

    // then
    assertThat(result.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(result.searchResultCursor()).isEqualTo("finished");

    verify(commandBuilder)
        .appendFinishInitializationCommand(taskResultBuilder, BATCH_OPERATION_KEY);
    verify(commandBuilder).appendExecutionCommand(taskResultBuilder, BATCH_OPERATION_KEY);
    verify(metrics).recordItemsPerPartition(0, BatchOperationType.CANCEL_PROCESS_INSTANCE);
  }

  @Test
  void shouldUpdateTotalItemsCountCorrectly() {
    // given
    batchOperation.setNumTotalItems(5);

    final var items = List.of(createItem(1L), createItem(2L), createItem(3L));
    final var itemPage = new ItemPage(items, NEXT_SEARCH_CURSOR, 3L, true);
    final var pageResult = new PageProcessingResult(true, NEXT_SEARCH_CURSOR, 3, true);

    when(itemProvider.fetchItemPage(SEARCH_CURSOR, PAGE_SIZE)).thenReturn(itemPage);
    when(pageProcessor.processPage(batchOperation.getKey(), itemPage, taskResultBuilder))
        .thenReturn(pageResult);

    // when
    initializer.initializeBatchOperation(batchOperation, taskResultBuilder);

    // then
    verify(metrics).recordItemsPerPartition(8, BatchOperationType.CANCEL_PROCESS_INSTANCE); // 5 + 3
  }

  @Test
  void shouldInitializeDeleteProcessInstanceBatchOperation() {
    // given
    batchOperation.setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE);
    final var items = List.of(createItem(10L), createItem(11L));
    final var itemPage = new ItemPage(items, NEXT_SEARCH_CURSOR, 2L, true);
    final var pageResult = new PageProcessingResult(true, NEXT_SEARCH_CURSOR, 2, true);

    when(itemProvider.fetchItemPage(SEARCH_CURSOR, PAGE_SIZE)).thenReturn(itemPage);
    when(pageProcessor.processPage(batchOperation.getKey(), itemPage, taskResultBuilder))
        .thenReturn(pageResult);

    // when
    final var result = initializer.initializeBatchOperation(batchOperation, taskResultBuilder);

    // then
    assertThat(result.batchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(result.searchResultCursor()).isEqualTo("finished");
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

  private Item createItem(final long key) {
    return new Item(key, key + 1000, null);
  }
}
