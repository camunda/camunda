/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static io.camunda.zeebe.protocol.record.value.BatchOperationType.PROCESS_CANCELLATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState.BatchOperationVisitor;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
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
  @Mock private SearchClientsProxy searchClientsProxy;
  @Mock private KeyGenerator keyGenerator;
  @Mock private TaskResultBuilder taskResultBuilder;
  @Mock private ReadonlyStreamProcessorContext streamProcessorContext;
  @Mock private ProcessingScheduleService scheduleService;
  @Mock private BatchOperationState batchOperationState;
  @Mock private PersistedBatchOperation batchOperation;

  @Captor private ArgumentCaptor<Task> taskCaptor;
  @Captor private ArgumentCaptor<ProcessInstanceQuery> queryCaptor;
  @Captor private ArgumentCaptor<BatchOperationChunkRecord> chunkRecordCaptor;

  private BatchOperationExecutionScheduler scheduler;

  @BeforeEach
  public void setUp() {
    setUpBasicSchedulerBehaviour();

    when(batchOperation.getBatchOperationType()).thenReturn(PROCESS_CANCELLATION);
    when(batchOperation.getEntityFilter(eq(ProcessInstanceFilter.class)))
        .thenReturn(mock(ProcessInstanceFilter.class));
    doAnswer(
            invocation -> {
              final BatchOperationVisitor visitor = invocation.getArgument(0);
              visitor.visit(batchOperation);
              return null;
            })
        .when(batchOperationState)
        .foreachPendingBatchOperation(any(BatchOperationVisitor.class));

    scheduler =
        new BatchOperationExecutionScheduler(
            scheduledTaskStateFactory, searchClientsProxy, keyGenerator, Duration.ofSeconds(1));
  }

  @Test
  public void shouldAppendChunkForBatchOperations() {
    // given
    final var result =
        new SearchQueryResult.Builder<Long>().items(List.of(1L, 2L, 3L)).total(3).build();
    when(searchClientsProxy.searchProcessInstanceKeys(queryCaptor.capture())).thenReturn(result);

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).foreachPendingBatchOperation(any());
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationChunkIntent.CREATE), chunkRecordCaptor.capture());
    final var batchOperationChunkRecord = chunkRecordCaptor.getValue();
    assertThat(batchOperationChunkRecord.getEntityKeys().size()).isEqualTo(3);
  }

  @Test
  public void shouldQueryMultipleTimesIfTotalIsHigher() {
    // given
    final var result =
        new SearchQueryResult.Builder<Long>().items(List.of(1L, 2L, 3L)).total(6).build();
    when(searchClientsProxy.searchProcessInstanceKeys(queryCaptor.capture())).thenReturn(result);

    // when our scheduler fires
    execute();

    // then
    verify(batchOperationState).foreachPendingBatchOperation(any());
    verify(taskResultBuilder)
        .appendCommandRecord(
            anyLong(), eq(BatchOperationChunkIntent.CREATE), chunkRecordCaptor.capture());
    final var batchOperationChunkRecord = chunkRecordCaptor.getValue();
    assertThat(batchOperationChunkRecord.getEntityKeys().size()).isEqualTo(6);
  }

  @Test
  void shouldSkipAlreadyProcessedBatchOperations() {
    // given
    final var result =
        new SearchQueryResult.Builder<Long>().items(List.of(1L, 2L, 3L)).total(3).build();
    when(searchClientsProxy.searchProcessInstanceKeys(queryCaptor.capture())).thenReturn(result);
    execute();

    // when we execute it again
    execute();

    // we should only fetch keys once
    verify(batchOperationState, times(2)).foreachPendingBatchOperation(any());
    verify(taskResultBuilder, times(1))
        .appendCommandRecord(
            anyLong(), eq(BatchOperationChunkIntent.CREATE), chunkRecordCaptor.capture());
    final var batchOperationChunkRecord = chunkRecordCaptor.getValue();
    assertThat(batchOperationChunkRecord.getEntityKeys().size()).isEqualTo(3);
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
