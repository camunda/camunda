/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static io.camunda.zeebe.protocol.record.value.BatchOperationType.CANCEL_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.BatchOperationType.MIGRATE_PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.Item;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.ItemPage;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProviderFactory;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
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
  private static final int PARTITION_ID = 1;
  @Mock private Supplier<ScheduledTaskState> scheduledTaskStateFactory;
  @Mock private TaskResultBuilder taskResultBuilder;
  @Mock private ReadonlyStreamProcessorContext streamProcessorContext;
  @Mock private ProcessingScheduleService scheduleService;
  @Mock private BatchOperationState batchOperationState;
  @Mock private PersistedBatchOperation batchOperation;
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

    final var filter = FilterBuilders.processInstance().build();

    lenient().when(batchOperation.getBatchOperationType()).thenReturn(CANCEL_PROCESS_INSTANCE);
    lenient()
        .when(batchOperation.getEntityFilter(eq(ProcessInstanceFilter.class)))
        .thenReturn(filter);
    lenient()
        .when(batchOperation.getInitializationSearchQueryPageSize(anyInt()))
        .thenReturn(QUERY_PAGE_SIZE);
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));
    lenient().when(batchOperationState.exists(anyLong())).thenReturn(true);

    final var engineConfiguration = mock(EngineConfiguration.class);
    when(engineConfiguration.getBatchOperationSchedulerInterval()).thenReturn(SCHEDULER_INTERVAL);
    when(engineConfiguration.getBatchOperationChunkSize()).thenReturn(CHUNK_SIZE);
    when(engineConfiguration.getBatchOperationQueryPageSize()).thenReturn(QUERY_PAGE_SIZE);

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
    when(batchOperation.isSuspended()).thenReturn(true);

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder).build(); // is always called
    verifyNoMoreInteractions(taskResultBuilder);
  }

  @Test
  public void shouldAppendFailedEvent() {
    // given
    when(itemProvider.fetchItemPage(any(), anyInt()))
        .thenThrow(new RuntimeException("errors", new RuntimeException()));

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
    assertThat(error.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(error.getType()).isEqualTo(BatchOperationErrorType.QUERY_FAILED);
    assertThat(error.getMessage()).contains("errors");
  }

  @Test
  public void shouldReducePageSizeEventWhenFirstAppendFails() {
    // given
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
    when(itemProvider.fetchItemPage(any(), anyInt())).thenReturn(createItemPage(1L, 2L, 3L));
    when(taskResultBuilder.canAppendRecords(any(), any())).thenReturn(false);
    when(batchOperation.getInitializationSearchQueryPageSize(anyInt())).thenReturn(1);

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
    when(batchOperation.getInitializationSearchCursor()).thenReturn("1");
    when(batchOperation.getNumTotalItems()).thenReturn(10);
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
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);
    when(batchOperation.getBatchOperationType()).thenReturn(MIGRATE_PROCESS_INSTANCE);

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
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);

    // given
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
