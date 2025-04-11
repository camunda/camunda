/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static io.camunda.zeebe.engine.processing.batchoperation.BatchOperationExecutionScheduler.CHUNK_SIZE_IN_RECORD;
import static io.camunda.zeebe.protocol.record.value.BatchOperationType.PROCESS_CANCELLATION;
import static io.camunda.zeebe.protocol.record.value.BatchOperationType.RESOLVE_INCIDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState.BatchOperationVisitor;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
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

  @Mock private Supplier<ScheduledTaskState> scheduledTaskStateFactory;
  @Mock private BatchOperationItemKeyProvider entityKeyProvider;
  @Mock private TaskResultBuilder taskResultBuilder;
  @Mock private ReadonlyStreamProcessorContext streamProcessorContext;
  @Mock private ProcessingScheduleService scheduleService;
  @Mock private BatchOperationState batchOperationState;
  @Mock private PersistedBatchOperation batchOperation;

  @Captor private ArgumentCaptor<Task> taskCaptor;
  @Captor private ArgumentCaptor<BatchOperationChunkRecord> chunkRecordCaptor;

  private BatchOperationExecutionScheduler scheduler;

  @BeforeEach
  public void setUp() {
    setUpBasicSchedulerBehaviour();

    when(batchOperation.getBatchOperationType()).thenReturn(PROCESS_CANCELLATION);
    lenient()
        .when(batchOperation.getEntityFilter(eq(ProcessInstanceFilter.class)))
        .thenReturn(mock(ProcessInstanceFilter.class));
    doAnswer(
            invocation -> {
              final BatchOperationVisitor visitor = invocation.getArgument(0);
              visitor.visit(batchOperation);
              return null;
            })
        .when(batchOperationState)
        .foreachPendingBatchOperation(any(BatchOperationVisitor.class));
    lenient().when(batchOperationState.exists(anyLong())).thenReturn(true);

    scheduler =
        new BatchOperationExecutionScheduler(
            scheduledTaskStateFactory, entityKeyProvider, Duration.ofSeconds(1));
  }

  @Test
  public void shouldAppendFailedEvent() {
    // given
    when(batchOperation.getEntityFilter(eq(ProcessInstanceFilter.class)))
        .thenThrow(new RuntimeException("error", new RuntimeException()));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).foreachPendingBatchOperation(any());
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationIntent.START), any(BatchOperationCreationRecord.class));
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationIntent.FAIL), any(BatchOperationCreationRecord.class));

    // and should NOT append an execute command
    verify(taskResultBuilder, times(0))
        .appendCommandRecord(
            anyLong(), any(Intent.class), any(UnifiedRecordValue.class), anyLong());
  }

  @Test
  public void shouldAppendChunkForBatchOperations() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);

    // given
    when(entityKeyProvider.fetchProcessInstanceKeys(queryCaptor.capture(), any()))
        .thenReturn(Set.of(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).foreachPendingBatchOperation(any());
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationIntent.START), any(BatchOperationCreationRecord.class));
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationChunkIntent.CREATE), chunkRecordCaptor.capture());
    final var batchOperationChunkRecord = chunkRecordCaptor.getValue();
    assertThat(batchOperationChunkRecord.getItemKeys().size()).isEqualTo(3);
  }

  @Test
  public void shouldAppendChunkOfIncidents() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);
    when(batchOperation.getBatchOperationType()).thenReturn(RESOLVE_INCIDENT);

    // given
    when(entityKeyProvider.fetchIncidentKeys(queryCaptor.capture(), any()))
        .thenReturn(Set.of(1L, 2L, 3L));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).foreachPendingBatchOperation(any());
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationIntent.START), any(BatchOperationCreationRecord.class));
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationChunkIntent.CREATE), chunkRecordCaptor.capture());
    final var batchOperationChunkRecord = chunkRecordCaptor.getValue();
    assertThat(batchOperationChunkRecord.getItemKeys().size()).isEqualTo(3);
  }

  @Test
  public void shouldCreateMultipleChunks() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);

    // given
    final var queryItemKeys = LongStream.range(0, CHUNK_SIZE_IN_RECORD * 2).boxed().toList();
    when(entityKeyProvider.fetchProcessInstanceKeys(queryCaptor.capture(), any()))
        .thenReturn(new HashSet<>(queryItemKeys));

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).foreachPendingBatchOperation(any());
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationIntent.START), any(BatchOperationCreationRecord.class));
    verify(taskResultBuilder, times(2))
        .appendCommandRecord(
            anyLong(), eq(BatchOperationChunkIntent.CREATE), chunkRecordCaptor.capture());
    var batchOperationChunkRecord = chunkRecordCaptor.getAllValues().get(0);
    assertThat(batchOperationChunkRecord.getItemKeys().size()).isEqualTo(CHUNK_SIZE_IN_RECORD);
    assertThat(batchOperationChunkRecord.getItemKeys())
        .containsExactlyInAnyOrder(
            queryItemKeys.subList(0, CHUNK_SIZE_IN_RECORD).toArray(Long[]::new));
    batchOperationChunkRecord = chunkRecordCaptor.getAllValues().get(1);
    assertThat(batchOperationChunkRecord.getItemKeys().size()).isEqualTo(CHUNK_SIZE_IN_RECORD);
    assertThat(batchOperationChunkRecord.getItemKeys())
        .containsExactlyInAnyOrder(
            queryItemKeys
                .subList(CHUNK_SIZE_IN_RECORD, CHUNK_SIZE_IN_RECORD * 2)
                .toArray(Long[]::new));
  }

  @Test
  public void shouldRescheduleAtTheEndOfExecution() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceFilter.class);

    // given
    when(entityKeyProvider.fetchProcessInstanceKeys(queryCaptor.capture(), any()))
        .thenReturn(Set.of(1L));

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
}
