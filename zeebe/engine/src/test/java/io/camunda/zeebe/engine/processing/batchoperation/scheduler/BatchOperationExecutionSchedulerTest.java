/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static io.camunda.zeebe.protocol.record.value.BatchOperationType.CANCEL_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.BatchOperationType.MIGRATE_PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.Item;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.ItemPage;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProviderFactory;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationInitializationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BatchOperationExecutionSchedulerTest {

  public static final Duration SCHEDULER_INTERVAL = Duration.ofSeconds(1);
  public static final int CHUNK_SIZE = 5;
  public static final int QUERY_PAGE_SIZE = 10;
  public static final Duration QUERY_INITIAL_RETRY_DELAY = Duration.ofMillis(50);
  public static final Duration QUERY_MAX_RETRY_DELAY = Duration.ofMillis(500);
  public static final int QUERY_RETRY_MAX = 5;
  public static final int QUERY_RETRY_BACKOFF_FACTOR = 2;
  public static final long BATCH_OPERATION_KEY = 123456789L;
  private static final int PARTITION_ID = 1;
  @Mock private Supplier<ScheduledTaskState> scheduledTaskStateFactory;
  @Mock private TaskResultBuilder taskResultBuilder;
  @Mock private ReadonlyStreamProcessorContext streamProcessorContext;
  @Mock private ProcessingScheduleService scheduleService;
  @Mock private BatchOperationState batchOperationState;
  @Mock private ItemProviderFactory itemProviderFactory;
  @Mock private ItemProvider itemProvider;
  @Mock private BatchOperationMetrics metrics;

  @Captor private ArgumentCaptor<Task> taskCaptor;
  @Captor private ArgumentCaptor<BatchOperationChunkRecord> chunkRecordCaptor;

  @Captor
  private ArgumentCaptor<BatchOperationPartitionLifecycleRecord> lifecycleRecordArgumentCaptor;

  @Captor private ArgumentCaptor<BatchOperationInitializationRecord> initializeRecordArgumentCaptor;

  private BatchOperationExecutionScheduler scheduler;

  @BeforeEach
  public void setUp() {
    setUpBasicSchedulerBehaviour();
    lenient().when(batchOperationState.exists(anyLong())).thenReturn(true);

    final var engineConfiguration = mock(EngineConfiguration.class);
    when(engineConfiguration.getBatchOperationSchedulerInterval()).thenReturn(SCHEDULER_INTERVAL);
    when(engineConfiguration.getBatchOperationChunkSize()).thenReturn(CHUNK_SIZE);
    when(engineConfiguration.getBatchOperationQueryPageSize()).thenReturn(QUERY_PAGE_SIZE);
    when(engineConfiguration.getBatchOperationQueryRetryInitialDelay())
        .thenReturn(QUERY_INITIAL_RETRY_DELAY);
    when(engineConfiguration.getBatchOperationQueryRetryMaxDelay())
        .thenReturn(QUERY_MAX_RETRY_DELAY);
    when(engineConfiguration.getBatchOperationQueryRetryMax()).thenReturn(QUERY_RETRY_MAX);
    when(engineConfiguration.getBatchOperationQueryRetryBackoffFactor())
        .thenReturn(QUERY_RETRY_BACKOFF_FACTOR);

    lenient().when(itemProviderFactory.fromBatchOperation(any())).thenReturn(itemProvider);

    lenient().when(taskResultBuilder.canAppendRecords(any(), any())).thenReturn(true);
    lenient()
        .when(taskResultBuilder.appendCommandRecord(anyLong(), any(), any(), any()))
        .thenReturn(true);

    scheduler =
        new BatchOperationExecutionScheduler(
            scheduledTaskStateFactory,
            itemProviderFactory,
            engineConfiguration,
            PARTITION_ID,
            metrics);
  }

  @Test
  public void shouldDoNothingOnSuspendedBatchOperation() {
    // given
    final var batchOperation = createBatchOperation();
    batchOperation.setStatus(BatchOperationStatus.SUSPENDED);
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder).build(); // is always called
    verifyNoMoreInteractions(taskResultBuilder);
  }

  @Test
  public void shouldDoNothingWhenNoBatchOperation() {
    // given
    when(batchOperationState.getNextPendingBatchOperation()).thenReturn(Optional.empty());

    // when our scheduler fires
    execute();

    // then
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder).build(); // is always called
    verifyNoMoreInteractions(taskResultBuilder);
  }

  @Test
  void shouldSkipReInitializationIfAlreadyOngoingWithDifferentCursor() {
    // given
    final String page1EndCursor = "10";
    final String page2EndCursor = "20";
    when(itemProvider.fetchItemPage(any(), anyInt()))
        .thenReturn(
            createItemPage(new long[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, page1EndCursor, false))
        .thenReturn(
            createItemPage(
                new long[] {11, 12, 13, 14, 15, 16, 17, 18, 19, 20}, page2EndCursor, false))
        .thenReturn(
            createItemPage(new long[] {21, 22, 23, 24, 25, 26, 27, 28, 29, 30}, "30", true));

    // each execution can only append records once
    when(taskResultBuilder.canAppendRecords(any(), any()))
        .thenReturn(true) // true to append 1 page of chunks
        .thenReturn(false) // false to stop and continue in another execution
        .thenReturn(true)
        .thenReturn(false)
        .thenReturn(true)
        .thenReturn(false);

    // keeps returning the same cursor however the second page returned a different cursor
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(createBatchOperation()))
        .thenReturn(
            Optional.of(createBatchOperation().setInitializationSearchCursor(page1EndCursor)))
        .thenReturn(
            Optional.of(createBatchOperation().setInitializationSearchCursor(page1EndCursor)));

    // when our scheduler fires
    execute();
    execute();
    execute(); // 3rd execution should be skipped since the state returns an old cursor

    // then
    // it will only fetch items first 2 times and ignore the third execution
    verify(itemProviderFactory, times(2)).fromBatchOperation(any());
    verifyNoMoreInteractions(itemProviderFactory);
  }

  @Test
  void shouldSkipReInitializationIfAlreadyFinished() {
    // given
    final String page1EndCursor = "10";
    final String page2EndCursor = "20";
    when(itemProvider.fetchItemPage(any(), anyInt()))
        .thenReturn(
            createItemPage(new long[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, page1EndCursor, false))
        .thenReturn(
            createItemPage(
                new long[] {11, 12, 13, 14, 15, 16, 17, 18, 19, 20}, page2EndCursor, true));

    // each execution can only append records once
    when(taskResultBuilder.canAppendRecords(any(), any()))
        .thenReturn(true) // true to append 1 page of chunks
        .thenReturn(false) // false to stop and continue in another execution
        .thenReturn(true)
        .thenReturn(false)
        .thenReturn(true)
        .thenReturn(false);

    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(createBatchOperation()))
        .thenReturn(
            Optional.of(createBatchOperation().setInitializationSearchCursor(page1EndCursor)))
        .thenReturn(
            Optional.of(createBatchOperation().setInitializationSearchCursor(page2EndCursor)));

    // when our scheduler fires
    execute();
    execute();
    execute(); // will be skipped, since initialization was done in a previous execution

    // then
    // it will only fetch items first 2 times and ignore the third execution
    verify(itemProviderFactory, times(2)).fromBatchOperation(any());
    verifyNoMoreInteractions(itemProviderFactory);

    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.FINISH_INITIALIZATION),
            any(BatchOperationInitializationRecord.class),
            any());
  }

  @Test
  void shouldExecuteANewSubsequentBatchOperation() {
    // given
    final String page1EndCursor = "10";
    final String page2EndCursor = "20";
    when(itemProvider.fetchItemPage(any(), anyInt()))
        .thenReturn(
            createItemPage(new long[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, page1EndCursor, false))
        .thenReturn(
            createItemPage(
                new long[] {11, 12, 13, 14, 15, 16, 17, 18, 19, 20}, page2EndCursor, true))
        .thenReturn(
            createItemPage(new long[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, page1EndCursor, true));

    when(taskResultBuilder.canAppendRecords(any(), any()))
        .thenReturn(true) // true to append 1 page of chunks
        .thenReturn(false) // false to stop and continue in another execution
        .thenReturn(true) // can append second page of first operation
        .thenReturn(true); // can append second operation

    final var batchOperation = createBatchOperation();
    final long bo1Key = batchOperation.getKey();
    final long bo2Key = bo1Key + 1;
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation))
        .thenReturn(
            Optional.of(createBatchOperation().setInitializationSearchCursor(page1EndCursor)))
        .thenReturn(Optional.of(createBatchOperation().setKey(bo2Key)));

    // when our scheduler fires
    execute();
    execute();
    execute(); // will be executed, since the next batch operation is different

    // then
    // it will only fetch items first 2 times and ignore the third execution
    verify(itemProviderFactory, times(3)).fromBatchOperation(any());
    verifyNoMoreInteractions(itemProviderFactory);

    verify(taskResultBuilder)
        .appendCommandRecord(
            eq(bo1Key),
            eq(BatchOperationIntent.FINISH_INITIALIZATION),
            any(BatchOperationInitializationRecord.class),
            any());
    verify(taskResultBuilder)
        .appendCommandRecord(
            eq(bo2Key),
            eq(BatchOperationIntent.FINISH_INITIALIZATION),
            any(BatchOperationInitializationRecord.class),
            any());
  }

  @Test
  public void shouldAppendFailedEvent() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));
    when(itemProvider.fetchItemPage(any(), anyInt()))
        .thenThrow(new RuntimeException("errors", new RuntimeException()));

    // when our scheduler fires three times (two retries)
    execute();
    execute();
    execute();
    execute();
    execute();
    execute();

    // then
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.FAIL),
            lifecycleRecordArgumentCaptor.capture(),
            any());

    // and should NOT append an execute command
    verifyNoExecuteCommandAppended();

    // and should contain an errors
    final var error = lifecycleRecordArgumentCaptor.getValue().getError();
    assertThat(error).isNotNull();
    assertThat(error.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(error.getType()).isEqualTo(BatchOperationErrorType.QUERY_FAILED);
    assertThat(error.getMessage()).contains("errors");

    final var durationCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(scheduleService, atLeastOnce()).runDelayedAsync(durationCaptor.capture(), any(), any());

    // default scheduler delay is 1000ms, initial retry delay is 100ms.
    // After the failure we are back at 1000ms
    assertThat(durationCaptor.getAllValues())
        .extracting(Duration::toMillis)
        .containsSubsequence(1000L, 50L, 100L, 200L, 400L, 500L, 1000L);
  }

  @Test
  public void shouldSaveCursorOnErrorEvent() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));
    when(itemProvider.fetchItemPage(any(), anyInt()))
        .thenReturn(createItemPage(new long[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, "10", false))
        .thenThrow(new CamundaSearchException("errors"));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.INITIALIZE),
            initializeRecordArgumentCaptor.capture(),
            any());

    // and should NOT append an execute command
    verifyNoExecuteCommandAppended();

    // and should contain an errors
    final var record = initializeRecordArgumentCaptor.getValue();
    assertThat(record).isNotNull();
    assertThat(record.getSearchResultCursor()).isEqualTo("10");
  }

  @Test
  public void shouldReducePageSizeEventWhenFirstAppendFails() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));
    when(itemProvider.fetchItemPage(any(), anyInt())).thenReturn(createItemPage(1L, 2L, 3L));
    when(taskResultBuilder.canAppendRecords(any(), any())).thenReturn(false);

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.INITIALIZE),
            initializeRecordArgumentCaptor.capture(),
            any());

    // and should NOT append an execute command
    verifyNoExecuteCommandAppended();

    // and should contain an errors
    final var value = initializeRecordArgumentCaptor.getValue();
    assertThat(value).isNotNull();
    assertThat(value.getSearchResultCursor()).isEqualTo("");
    assertThat(value.getSearchQueryPageSize()).isEqualTo(5);
  }

  @Test
  public void shouldFailWhenPageSizeCannotBeReduced() {
    // given
    final var batchOperation = createBatchOperation();
    batchOperation.setInitializationSearchQueryPageSize(1);
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));
    when(itemProvider.fetchItemPage(any(), anyInt())).thenReturn(createItemPage(1L, 2L, 3L));
    when(taskResultBuilder.canAppendRecords(any(), any())).thenReturn(false);

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.FAIL),
            lifecycleRecordArgumentCaptor.capture(),
            any());

    // and should NOT append an execute command
    verifyNoExecuteCommandAppended();

    // and should contain an errors
    final var error = lifecycleRecordArgumentCaptor.getValue().getError();
    assertThat(error).isNotNull();
    assertThat(error.getMessage())
        .startsWith("Unable to append first chunk of batch operation items");
  }

  @Test
  public void shouldContinueInitializationForBigBatchOperations() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));
    when(itemProvider.fetchItemPage(any(), anyInt()))
        .thenReturn(createItemPage(new long[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, "0", false))
        .thenReturn(createItemPage(new long[] {11, 12, 13, 14, 15, 16, 17, 18, 19, 20}, "1", true));
    when(taskResultBuilder.canAppendRecords(any(), any())).thenReturn(true).thenReturn(false);
    // when our scheduler fires
    execute();

    // then
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.INITIALIZE),
            initializeRecordArgumentCaptor.capture(),
            any());

    // and should NOT append an execute command
    verifyNoExecuteCommandAppended();

    // and should contain an errors
    final var recordValue = initializeRecordArgumentCaptor.getValue();
    assertThat(recordValue).isNotNull();
    assertThat(recordValue.getSearchResultCursor()).isEqualTo("0");
  }

  @Test
  public void shouldAppendChunkForBatchOperations() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));
    when(itemProvider.fetchItemPage(any(), anyInt())).thenReturn(createItemPage(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationChunkIntent.CREATE), chunkRecordCaptor.capture(), any());
    final var batchOperationChunkRecord = chunkRecordCaptor.getValue();
    assertThat(batchOperationChunkRecord.getItems().size()).isEqualTo(3);

    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.FINISH_INITIALIZATION),
            any(BatchOperationInitializationRecord.class),
            any());

    verify(taskResultBuilder)
        .appendCommandRecord(anyLong(), eq(BatchOperationExecutionIntent.EXECUTE), any(), any());

    verify(metrics).recordItemsPerPartition(3, CANCEL_PROCESS_INSTANCE);
  }

  @Test
  public void shouldAppendNextChunkForBatchOperations() {
    // given
    final var batchOperation = createBatchOperation();
    batchOperation.setInitializationSearchCursor("1");
    batchOperation.setNumTotalItems(10);
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));
    when(itemProvider.fetchItemPage(eq("1"), anyInt())).thenReturn(createItemPage(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationChunkIntent.CREATE), chunkRecordCaptor.capture(), any());
    final var batchOperationChunkRecord = chunkRecordCaptor.getValue();
    assertThat(batchOperationChunkRecord.getItems().size()).isEqualTo(3);

    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.FINISH_INITIALIZATION),
            any(BatchOperationInitializationRecord.class),
            any());

    verify(taskResultBuilder)
        .appendCommandRecord(anyLong(), eq(BatchOperationExecutionIntent.EXECUTE), any(), any());

    verify(metrics).recordItemsPerPartition(13, CANCEL_PROCESS_INSTANCE);
  }

  @Test
  public void shouldAppendChunkOfProcessMigrationItemKeys() {
    final var batchOperation = createBatchOperation();
    batchOperation.setBatchOperationType(MIGRATE_PROCESS_INSTANCE);
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    // given
    when(itemProvider.fetchItemPage(any(), anyInt())).thenReturn(createItemPage(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationChunkIntent.CREATE), chunkRecordCaptor.capture(), any());
    final var batchOperationChunkRecord = chunkRecordCaptor.getValue();
    assertThat(batchOperationChunkRecord.getItems().size()).isEqualTo(3);

    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.FINISH_INITIALIZATION),
            any(BatchOperationInitializationRecord.class),
            any());
  }

  @Test
  public void shouldCreateMultipleChunks() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));
    final var queryItems = LongStream.range(0, CHUNK_SIZE * 2).toArray();
    when(itemProvider.fetchItemPage(any(), anyInt())).thenReturn(createItemPage(queryItems));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder, times(2))
        .appendCommandRecord(
            anyLong(), eq(BatchOperationChunkIntent.CREATE), chunkRecordCaptor.capture(), any());
    var batchOperationChunkRecord = chunkRecordCaptor.getAllValues().getFirst();
    assertThat(batchOperationChunkRecord.getItems().size()).isEqualTo(CHUNK_SIZE);
    assertThat(extractRecordItemKeys(batchOperationChunkRecord.getItems()))
        .containsExactlyInAnyOrder(
            extractQueryItemKeys(queryItems, 0, CHUNK_SIZE).toArray(Long[]::new));
    batchOperationChunkRecord = chunkRecordCaptor.getAllValues().get(1);
    assertThat(batchOperationChunkRecord.getItems().size()).isEqualTo(CHUNK_SIZE);
    assertThat(extractRecordItemKeys(batchOperationChunkRecord.getItems()))
        .containsExactlyInAnyOrder(
            extractQueryItemKeys(queryItems, CHUNK_SIZE, CHUNK_SIZE).toArray(Long[]::new));

    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.FINISH_INITIALIZATION),
            any(BatchOperationInitializationRecord.class),
            any());

    verify(taskResultBuilder)
        .appendCommandRecord(anyLong(), eq(BatchOperationExecutionIntent.EXECUTE), any(), any());

    verify(metrics).recordItemsPerPartition(CHUNK_SIZE * 2, CANCEL_PROCESS_INSTANCE);
  }

  @Test
  public void shouldRescheduleAtTheEndOfExecution() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));
    when(itemProvider.fetchItemPage(any(), anyInt())).thenReturn(createItemPage(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    verify(scheduleService, times(2)).runDelayedAsync(any(), taskCaptor.capture(), any());
  }

  /** Bypasses the scheduling mechanism and executes the task directly */
  private void execute() {
    scheduler.onRecovered(streamProcessorContext);
    taskCaptor.getValue().execute(taskResultBuilder);
  }

  private void setUpBasicSchedulerBehaviour() {
    when(scheduledTaskStateFactory.get()).thenReturn(mock(ScheduledTaskState.class));
    when(scheduledTaskStateFactory.get().getBatchOperationState()).thenReturn(batchOperationState);
    when(streamProcessorContext.getScheduleService()).thenReturn(scheduleService);
    when(scheduleService.runDelayedAsync(any(), taskCaptor.capture(), any())).thenReturn(null);
  }

  private static PersistedBatchOperation createBatchOperation() {
    final var filter = FilterBuilders.processInstance().build();

    return new PersistedBatchOperation()
        .setKey(BATCH_OPERATION_KEY)
        .setStatus(BatchOperationStatus.CREATED)
        .setBatchOperationType(CANCEL_PROCESS_INSTANCE)
        .setInitializationSearchQueryPageSize(QUERY_PAGE_SIZE)
        .setEntityFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(filter)));
  }

  private ItemPage createItemPage(final long... itemKeys) {
    return createItemPage(itemKeys, Long.toString(itemKeys[itemKeys.length - 1]), true);
  }

  private ItemPage createItemPage(
      final long[] itemKeys, final String endCursor, final boolean isLastPage) {
    return new ItemPage(
        LongStream.of(itemKeys)
            .mapToObj(itemKey -> new Item(itemKey, itemKey))
            .collect(Collectors.toList()),
        endCursor,
        itemKeys.length,
        isLastPage);
  }

  private static Collection<Long> extractQueryItemKeys(
      final long[] itemKeys, final int offset, final int limit) {
    return Arrays.stream(itemKeys).boxed().skip(offset).limit(limit).collect(Collectors.toSet());
  }

  private static Collection<Long> extractRecordItemKeys(
      final Collection<BatchOperationItemValue> items) {
    return extractRecordItemKeys(items, 0, items.size());
  }

  private static Collection<Long> extractRecordItemKeys(
      final Collection<BatchOperationChunkRecordValue.BatchOperationItemValue> items,
      final int offset,
      final int limit) {
    return items.stream()
        .map(BatchOperationItemValue::getItemKey)
        .skip(offset)
        .limit(limit)
        .collect(Collectors.toSet());
  }

  private void verifyNoExecuteCommandAppended() {
    verify(taskResultBuilder, times(0))
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationExecutionIntent.EXECUTE),
            any(UnifiedRecordValue.class),
            any());
  }
}
