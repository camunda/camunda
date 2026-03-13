/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.Item;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.ItemPage;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationChunkAppender.ChunkingOutcome;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationItem;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchOperationChunkAppenderTest {

  private static final long BATCH_OPERATION_KEY = 123L;
  private static final int CHUNK_SIZE = 2;

  private BatchOperationChunkAppender processor;
  private ItemProvider mockItemProvider;
  private TaskResultBuilder mockTaskResultBuilder;

  @BeforeEach
  void setUp() {
    processor = new BatchOperationChunkAppender(CHUNK_SIZE);
    mockItemProvider = mock(ItemProvider.class);
    mockTaskResultBuilder = mock(TaskResultBuilder.class);
  }

  @Test
  void shouldChunkAndAppendWithSingleChunk() {
    // given
    final var item1 = new Item(100L, 200L, null);
    final var item2 = new Item(101L, 201L, null);
    final var page = new ItemPage(List.of(item1, item2), "cursor123", 2L, false);
    final var context = createContext("cursor0", 10);

    when(mockItemProvider.fetchItemPage("cursor0", 10)).thenReturn(page);
    when(mockTaskResultBuilder.canAppendRecords(any(), any())).thenReturn(true);

    // when
    final var result =
        processor.fetchAndChunkNextPage(mockItemProvider, context, mockTaskResultBuilder);

    // then
    assertThat(result).isInstanceOf(ChunkingOutcome.Continue.class);
    final var continueResult = (ChunkingOutcome.Continue) result;
    assertThat(continueResult.endCursor()).isEqualTo("cursor123");
    assertThat(continueResult.itemsProcessed()).isEqualTo(2);

    verify(mockTaskResultBuilder, times(1))
        .appendCommandRecord(
            eq(BATCH_OPERATION_KEY),
            eq(BatchOperationChunkIntent.CREATE),
            any(BatchOperationChunkRecord.class),
            any(FollowUpCommandMetadata.class));
  }

  @Test
  void shouldChunkAndAppendWithMultipleChunks() {
    // given - 5 items with chunk size 2 should create 3 chunks (2+2+1)
    final var items =
        List.of(
            new Item(100L, 200L, null),
            new Item(101L, 201L, null),
            new Item(102L, 202L, null),
            new Item(103L, 203L, null),
            new Item(104L, 204L, null));
    final var page = new ItemPage(items, "cursor456", 5L, true);
    final var context = createContext("cursor0", 10);

    when(mockItemProvider.fetchItemPage("cursor0", 10)).thenReturn(page);
    when(mockTaskResultBuilder.canAppendRecords(any(), any())).thenReturn(true);

    // when
    final var result =
        processor.fetchAndChunkNextPage(mockItemProvider, context, mockTaskResultBuilder);

    // then
    assertThat(result).isInstanceOf(ChunkingOutcome.Finished.class);
    final var finishedResult = (ChunkingOutcome.Finished) result;
    assertThat(finishedResult.endCursor()).isEqualTo("cursor456");
    assertThat(finishedResult.itemsProcessed()).isEqualTo(5);

    // Should create 3 chunks: [2 items], [2 items], [1 item]
    verify(mockTaskResultBuilder, times(3))
        .appendCommandRecord(
            eq(BATCH_OPERATION_KEY),
            eq(BatchOperationChunkIntent.CREATE),
            any(BatchOperationChunkRecord.class),
            any(FollowUpCommandMetadata.class));
  }

  @Test
  void shouldNotAppendChunksWhenTaskResultBuilderCannotAccommodate() {
    // given
    final var items = List.of(new Item(100L, 200L, null), new Item(101L, 201L, null));
    final var page = new ItemPage(items, "cursor789", 2L, false);
    final var context = createContext("cursor0", 10);

    when(mockItemProvider.fetchItemPage("cursor0", 10)).thenReturn(page);
    when(mockTaskResultBuilder.canAppendRecords(
            any(List.class), any(FollowUpCommandMetadata.class)))
        .thenReturn(false);

    // when
    final var result =
        processor.fetchAndChunkNextPage(mockItemProvider, context, mockTaskResultBuilder);

    // then
    assertThat(result).isInstanceOf(ChunkingOutcome.BufferFull.class);

    verify(mockTaskResultBuilder, never())
        .appendCommandRecord(
            any(Long.class),
            any(),
            any(BatchOperationChunkRecord.class),
            any(FollowUpCommandMetadata.class));
  }

  @Test
  void shouldProcessEmptyPage() {
    // given
    final var page = new ItemPage(List.of(), null, 0L, true);
    final var context = createContext("cursor0", 10);

    when(mockItemProvider.fetchItemPage("cursor0", 10)).thenReturn(page);
    when(mockTaskResultBuilder.canAppendRecords(
            any(List.class), any(FollowUpCommandMetadata.class)))
        .thenReturn(true);

    // when
    final var result =
        processor.fetchAndChunkNextPage(mockItemProvider, context, mockTaskResultBuilder);

    // then
    assertThat(result).isInstanceOf(ChunkingOutcome.Finished.class);
    final var finishedResult = (ChunkingOutcome.Finished) result;
    assertThat(finishedResult.endCursor()).isNull();
    assertThat(finishedResult.itemsProcessed()).isZero();

    // Should not append any chunks for empty page
    verify(mockTaskResultBuilder, never())
        .appendCommandRecord(
            any(Long.class),
            any(),
            any(BatchOperationChunkRecord.class),
            any(FollowUpCommandMetadata.class));
  }

  @Test
  void shouldCreateCorrectChunkRecords() {
    // given
    final var item1 = new Item(100L, 200L, null);
    final var item2 = new Item(101L, 201L, 111L);
    final var item3 = new Item(102L, 202L, 112L);
    final var items = List.of(item1, item2, item3);
    final var page = new ItemPage(items, "cursor", 3L, false);
    final var context = createContext("cursor0", 10);

    when(mockItemProvider.fetchItemPage("cursor0", 10)).thenReturn(page);
    when(mockTaskResultBuilder.canAppendRecords(any(), any())).thenReturn(true);

    // when
    processor.fetchAndChunkNextPage(mockItemProvider, context, mockTaskResultBuilder);

    // then
    final var chunkCaptor = ArgumentCaptor.forClass(BatchOperationChunkRecord.class);
    verify(mockTaskResultBuilder, times(2))
        .appendCommandRecord(
            eq(BATCH_OPERATION_KEY),
            eq(BatchOperationChunkIntent.CREATE),
            chunkCaptor.capture(),
            any(FollowUpCommandMetadata.class));

    final var capturedChunks = chunkCaptor.getAllValues();
    assertThat(capturedChunks).hasSize(2);

    // First chunk should have 2 items (chunk size)
    assertThat(capturedChunks.get(0).getBatchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(capturedChunks.get(0).getItems())
        .containsExactlyInAnyOrder(
            new BatchOperationItem(100L, 200L, -1L), new BatchOperationItem(101L, 201L, 111L));

    // Second chunk should have 1 item (remainder)
    assertThat(capturedChunks.get(1).getBatchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(capturedChunks.get(1).getItems())
        .containsExactly(new BatchOperationItem(102L, 202L, 112L));
  }

  @Test
  void shouldHandleDifferentChunkSizes() {
    // given
    final var largeChunkProcessor = new BatchOperationChunkAppender(10);
    final var items =
        List.of(new Item(100L, 200L, null), new Item(101L, 201L, null), new Item(102L, 202L, null));
    final var page = new ItemPage(items, "cursor", 3L, false);
    final var context = createContext("cursor0", 10);

    when(mockItemProvider.fetchItemPage("cursor0", 10)).thenReturn(page);
    when(mockTaskResultBuilder.canAppendRecords(any(), any())).thenReturn(true);

    // when
    final var result =
        largeChunkProcessor.fetchAndChunkNextPage(mockItemProvider, context, mockTaskResultBuilder);

    // then
    assertThat(result).isInstanceOf(ChunkingOutcome.Continue.class);
    final var continueResult = (ChunkingOutcome.Continue) result;
    assertThat(continueResult.itemsProcessed()).isEqualTo(3);

    // Should create only 1 chunk since all items fit in chunk size 10
    verify(mockTaskResultBuilder, times(1))
        .appendCommandRecord(
            eq(BATCH_OPERATION_KEY),
            eq(BatchOperationChunkIntent.CREATE),
            any(BatchOperationChunkRecord.class),
            any(FollowUpCommandMetadata.class));
  }

  @Test
  void shouldReturnFetchFailedWhenItemProviderThrows() {
    // given
    final var context = createContext("cursor1", 50);
    final var exception = new RuntimeException("Database connection failed");

    when(mockItemProvider.fetchItemPage("cursor1", 50)).thenThrow(exception);

    // when
    final var result =
        processor.fetchAndChunkNextPage(mockItemProvider, context, mockTaskResultBuilder);

    // then
    assertThat(result).isInstanceOf(ChunkingOutcome.FetchFailed.class);
    final var fetchFailedResult = (ChunkingOutcome.FetchFailed) result;
    assertThat(fetchFailedResult.cause()).isSameAs(exception);

    verify(mockTaskResultBuilder, never())
        .appendCommandRecord(any(Long.class), any(), any(), any(FollowUpCommandMetadata.class));
  }

  @Test
  void shouldUseCursorAndPageSizeFromContext() {
    // given
    final var context = createContext("specific-cursor", 25);
    final var page = new ItemPage(List.of(), null, 0L, true);

    when(mockItemProvider.fetchItemPage("specific-cursor", 25)).thenReturn(page);
    when(mockTaskResultBuilder.canAppendRecords(any(), any())).thenReturn(true);

    // when
    processor.fetchAndChunkNextPage(mockItemProvider, context, mockTaskResultBuilder);

    // then
    verify(mockItemProvider).fetchItemPage("specific-cursor", 25);
  }

  private static InitializationContext createContext(final String cursor, final int pageSize) {
    final var batchOperation = new PersistedBatchOperation().setKey(BATCH_OPERATION_KEY);
    return new InitializationContext(batchOperation, cursor, pageSize, 0, false);
  }
}
