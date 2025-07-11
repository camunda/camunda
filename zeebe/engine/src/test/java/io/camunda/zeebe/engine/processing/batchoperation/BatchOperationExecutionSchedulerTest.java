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
import static io.camunda.zeebe.protocol.record.value.BatchOperationType.MODIFY_PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.value.BatchOperationType.RESOLVE_INCIDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.BatchOperationItemProvider.Item;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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
  public static final int CHUNK_SIZE = 10;
  private static final int PARTITION_ID = 1;
  @Mock private Supplier<ScheduledTaskState> scheduledTaskStateFactory;
  @Mock private BatchOperationItemProvider entityKeyProvider;
  @Mock private TaskResultBuilder taskResultBuilder;
  @Mock private ReadonlyStreamProcessorContext streamProcessorContext;
  @Mock private ProcessingScheduleService scheduleService;
  @Mock private BatchOperationState batchOperationState;
  @Mock private PersistedBatchOperation batchOperation;

  @Captor private ArgumentCaptor<Task> taskCaptor;
  @Captor private ArgumentCaptor<BatchOperationChunkRecord> chunkRecordCaptor;

  @Captor
  private ArgumentCaptor<BatchOperationPartitionLifecycleRecord> lifecycleRecordArgumentCaptor;

  private BatchOperationExecutionScheduler scheduler;

  @BeforeEach
  public void setUp() {
    setUpBasicSchedulerBehaviour();

    final var filter = FilterBuilders.processInstance().build();

    when(batchOperation.getBatchOperationType()).thenReturn(CANCEL_PROCESS_INSTANCE);
    lenient()
        .when(batchOperation.getEntityFilter(eq(ProcessInstanceFilter.class)))
        .thenReturn(filter);
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));
    lenient().when(batchOperationState.exists(anyLong())).thenReturn(true);

    final var engineConfiguration = mock(EngineConfiguration.class);
    when(engineConfiguration.getBatchOperationSchedulerInterval()).thenReturn(SCHEDULER_INTERVAL);
    when(engineConfiguration.getBatchOperationChunkSize()).thenReturn(CHUNK_SIZE);

    scheduler =
        new BatchOperationExecutionScheduler(
            scheduledTaskStateFactory,
            entityKeyProvider,
            engineConfiguration,
            PARTITION_ID,
            mock(BatchOperationMetrics.class));
  }

  @Test
  public void shouldAppendFailedEvent() {
    // given
    when(batchOperation.getEntityFilter(eq(ProcessInstanceFilter.class)))
        .thenThrow(new RuntimeException("errors", new RuntimeException()));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.START),
            any(BatchOperationCreationRecord.class),
            any());
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.FAIL),
            lifecycleRecordArgumentCaptor.capture(),
            any());

    // and should NOT append an execute command
    verify(taskResultBuilder, times(0))
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationExecutionIntent.EXECUTE),
            any(UnifiedRecordValue.class),
            any());

    // and should contain an errors
    final var error = lifecycleRecordArgumentCaptor.getValue().getError();
    assertThat(error).isNotNull();
    assertThat(error.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(error.getType()).isEqualTo(BatchOperationErrorType.QUERY_FAILED);
    assertThat(error.getMessage()).contains("errors");
  }

  @Test
  public void shouldAppendChunkForBatchOperations() {
    // given
    when(entityKeyProvider.fetchProcessInstanceItems(eq(PARTITION_ID), any(), any(), any()))
        .thenReturn(createItems(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.START),
            any(BatchOperationCreationRecord.class),
            any());
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationIntent.CREATE_CHUNK), chunkRecordCaptor.capture(), any());
    final var batchOperationChunkRecord = chunkRecordCaptor.getValue();
    assertThat(batchOperationChunkRecord.getItems().size()).isEqualTo(3);
  }

  @Test
  public void shouldQueryOnlyActiveRootProcessInstancesWhenCancelProcessInstancesBatch() {
    final var filterCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);

    // given
    when(entityKeyProvider.fetchProcessInstanceItems(
            eq(PARTITION_ID), filterCaptor.capture(), any(), any()))
        .thenReturn(createItems(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    final var filter = filterCaptor.getValue();
    assertThat(filter.stateOperations()).containsExactly(Operation.eq("ACTIVE"));
    assertThat(filter.parentProcessInstanceKeyOperations())
        .containsExactly(Operation.exists(false));
  }

  @Test
  public void shouldQueryOnlyActiveProcessInstancesWhenMigrateProcessInstancesBatch() {
    final var filterCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);
    when(batchOperation.getBatchOperationType()).thenReturn(MIGRATE_PROCESS_INSTANCE);

    // given
    when(entityKeyProvider.fetchProcessInstanceItems(
            eq(PARTITION_ID), filterCaptor.capture(), any(), any()))
        .thenReturn(createItems(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    final var filter = filterCaptor.getValue();
    assertThat(filter.stateOperations()).containsExactly(Operation.eq("ACTIVE"));
    assertThat(filter.parentProcessInstanceKeyOperations()).isEmpty();
  }

  @Test
  public void shouldQueryOnlyActiveProcessInstancesWhenModifyProcessInstancesBatch() {
    final var filterCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);
    when(batchOperation.getBatchOperationType()).thenReturn(MODIFY_PROCESS_INSTANCE);

    // given
    when(entityKeyProvider.fetchProcessInstanceItems(
            eq(PARTITION_ID), filterCaptor.capture(), any(), any()))
        .thenReturn(createItems(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    final var filter = filterCaptor.getValue();
    assertThat(filter.stateOperations()).containsExactly(Operation.eq("ACTIVE"));
    assertThat(filter.parentProcessInstanceKeyOperations()).isEmpty();
  }

  @Test
  public void shouldQueryOnlyActiveProcessInstancesWhenResolveIncidentsBatch() {
    final var filterCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);
    when(batchOperation.getBatchOperationType()).thenReturn(RESOLVE_INCIDENT);

    // given
    when(entityKeyProvider.fetchIncidentItems(
            eq(PARTITION_ID), filterCaptor.capture(), any(), any()))
        .thenReturn(createItems(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    final var filter = filterCaptor.getValue();
    assertThat(filter.stateOperations()).containsExactly(Operation.eq("ACTIVE"));
    assertThat(filter.parentProcessInstanceKeyOperations()).isEmpty();
  }

  @Test
  public void shouldAppendChunkOfIncidents() {
    when(batchOperation.getBatchOperationType()).thenReturn(RESOLVE_INCIDENT);

    // given
    when(entityKeyProvider.fetchIncidentItems(eq(PARTITION_ID), any(), any(), any()))
        .thenReturn(createItems(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.START),
            any(BatchOperationCreationRecord.class),
            any());
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationIntent.CREATE_CHUNK), chunkRecordCaptor.capture(), any());
    final var batchOperationChunkRecord = chunkRecordCaptor.getValue();
    assertThat(batchOperationChunkRecord.getItems().size()).isEqualTo(3);
  }

  @Test
  public void shouldAppendChunkOfProcessMigrationItemKeys() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);
    when(batchOperation.getBatchOperationType()).thenReturn(MIGRATE_PROCESS_INSTANCE);

    // given
    when(entityKeyProvider.fetchProcessInstanceItems(
            eq(PARTITION_ID), queryCaptor.capture(), any(), any()))
        .thenReturn(createItems(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.START),
            any(BatchOperationCreationRecord.class),
            any());
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationIntent.CREATE_CHUNK), chunkRecordCaptor.capture(), any());
    final var batchOperationChunkRecord = chunkRecordCaptor.getValue();
    assertThat(batchOperationChunkRecord.getItems().size()).isEqualTo(3);
  }

  @Test
  public void shouldCreateMultipleChunks() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);

    // given
    final var queryItems = createItems(LongStream.range(0, CHUNK_SIZE * 2).toArray());
    when(entityKeyProvider.fetchProcessInstanceItems(
            eq(PARTITION_ID), queryCaptor.capture(), any(), any()))
        .thenReturn(new HashSet<>(queryItems));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(),
            eq(BatchOperationIntent.START),
            any(BatchOperationCreationRecord.class),
            any());
    verify(taskResultBuilder, times(2))
        .appendCommandRecord(
            anyLong(), eq(BatchOperationIntent.CREATE_CHUNK), chunkRecordCaptor.capture(), any());
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
  }

  @Test
  public void shouldRescheduleAtTheEndOfExecution() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);

    // given
    when(entityKeyProvider.fetchProcessInstanceItems(
            eq(PARTITION_ID), queryCaptor.capture(), any(), any()))
        .thenReturn(new HashSet<>(createItems(1L)));

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

  private Set<Item> createItems(final long... itemKeys) {
    return LongStream.of(itemKeys)
        .mapToObj(itemKey -> new Item(itemKey, itemKey))
        .collect(Collectors.toSet());
  }

  private static Collection<Long> extractQueryItemKeys(final Set<Item> items) {
    return extractQueryItemKeys(items, 0, items.size());
  }

  private static Collection<Long> extractQueryItemKeys(
      final Collection<Item> items, final int offset, final int limit) {
    return items.stream().map(Item::itemKey).skip(offset).limit(limit).collect(Collectors.toSet());
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
}
