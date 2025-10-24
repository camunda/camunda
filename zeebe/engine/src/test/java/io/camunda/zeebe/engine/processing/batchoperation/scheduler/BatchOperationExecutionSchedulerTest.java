/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static io.camunda.zeebe.protocol.record.value.BatchOperationType.CANCEL_PROCESS_INSTANCE;
import static org.mockito.Mockito.*;

import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializer.BatchOperationInitializationException;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationRetryHandler.RetryResult;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation.BatchOperationStatus;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.Optional;
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

  public static final Duration SCHEDULER_INTERVAL = Duration.ofSeconds(1);
  public static final long BATCH_OPERATION_KEY = 123456789L;

  @Mock private Supplier<ScheduledTaskState> scheduledTaskStateFactory;
  @Mock private TaskResultBuilder taskResultBuilder;
  @Mock private ReadonlyStreamProcessorContext streamProcessorContext;
  @Mock private ProcessingScheduleService scheduleService;
  @Mock private BatchOperationState batchOperationState;
  @Mock private BatchOperationInitializer batchOperationInitializer;
  @Mock private BatchOperationRetryHandler retryHandler;

  @Captor private ArgumentCaptor<Task> taskCaptor;

  private BatchOperationExecutionScheduler scheduler;

  @BeforeEach
  public void setUp() {
    setUpBasicSchedulerBehaviour();

    scheduler =
        new BatchOperationExecutionScheduler(
            scheduledTaskStateFactory, batchOperationInitializer, retryHandler, SCHEDULER_INTERVAL);
  }

  @Test
  public void shouldDoNothingWhenNoBatchOperation() {
    // given
    when(batchOperationState.getNextPendingBatchOperation()).thenReturn(Optional.empty());

    // when
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verifyNoInteractions(batchOperationInitializer, retryHandler);
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
  }

  @Test
  public void shouldDelegateToRetryHandlerWhenBatchOperationExists() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    when(retryHandler.executeWithRetry(any(), anyInt())).thenReturn(RetryResult.success("cursor"));

    // when
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(retryHandler).executeWithRetry(any(), anyInt());
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
  }

  @Test
  public void shouldHandleRetryResultSuccess() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    when(retryHandler.executeWithRetry(any(), anyInt()))
        .thenReturn(RetryResult.success("new-cursor"));

    // when
    execute();

    // then
    verify(retryHandler).executeWithRetry(any(), anyInt());
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
  }

  @Test
  public void shouldHandleRetryResultFailure() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    final var exception =
        new BatchOperationInitializationException(
            "Test error", BatchOperationErrorType.QUERY_FAILED, "cursor");
    when(retryHandler.executeWithRetry(any(), anyInt())).thenReturn(RetryResult.failure(exception));

    // when
    execute();

    // then
    verify(retryHandler).executeWithRetry(any(), anyInt());
    verify(batchOperationInitializer)
        .appendFailedCommand(taskResultBuilder, BATCH_OPERATION_KEY, exception);
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
  }

  @Test
  public void shouldHandleRetryResultRetry() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    final var retryDelay = Duration.ofMillis(500);
    when(retryHandler.executeWithRetry(any(), anyInt()))
        .thenReturn(RetryResult.retry(retryDelay, 1, "cursor"));

    // when
    execute();

    // then
    verify(retryHandler).executeWithRetry(any(), anyInt());
    verify(scheduleService)
        .runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any()); // initial schedule
    verify(scheduleService).runDelayedAsync(eq(retryDelay), any(), any()); // retry schedule
  }

  @Test
  public void shouldSkipReInitializationForSameBatchOperationWithDifferentCursor() {
    // given
    final var batchOperation = createBatchOperation().setInitializationSearchCursor("cursor1");
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation))
        .thenReturn(Optional.of(batchOperation.setInitializationSearchCursor("cursor2")));

    when(retryHandler.executeWithRetry(any(), anyInt())).thenReturn(RetryResult.success("cursor1"));

    // when - execute twice
    execute();
    execute();

    // then - should only process the first one, skip the second due to cursor change
    verify(retryHandler, times(1)).executeWithRetry(any(), anyInt());
  }

  @Test
  public void shouldProcessSameBatchOperationWithSameCursor() {
    // given
    final var batchOperation = createBatchOperation().setInitializationSearchCursor("cursor1");
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation))
        .thenReturn(Optional.of(batchOperation));

    when(retryHandler.executeWithRetry(any(), anyInt())).thenReturn(RetryResult.success("cursor1"));

    // when - execute twice
    execute();
    execute();

    // then - should process both since cursor is the same
    verify(retryHandler, times(2)).executeWithRetry(any(), anyInt());
  }

  @Test
  public void shouldProcessNewBatchOperationAfterDifferentKey() {
    // given
    final var batchOperation1 = createBatchOperation();
    final var batchOperation2 = createBatchOperation().setKey(BATCH_OPERATION_KEY + 1);

    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation1))
        .thenReturn(Optional.of(batchOperation2));

    when(retryHandler.executeWithRetry(any(), anyInt()))
        .thenReturn(RetryResult.success("cursor1"))
        .thenReturn(RetryResult.success("cursor2"));

    // when
    execute();
    execute();

    // then - should process both operations
    verify(retryHandler, times(2)).executeWithRetry(any(), anyInt());
  }

  @Test
  public void shouldRescheduleWithInitialIntervalAfterProcessing() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    when(retryHandler.executeWithRetry(any(), anyInt())).thenReturn(RetryResult.success("cursor"));

    // when
    execute();

    // then
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
  }

  /** Bypasses the scheduling mechanism and executes the task directly */
  private void execute() {
    scheduler.onRecovered(streamProcessorContext);
    taskCaptor.getValue().execute(taskResultBuilder);
  }

  private void setUpBasicSchedulerBehaviour() {
    final var scheduledTaskState = mock(ScheduledTaskState.class);
    when(scheduledTaskStateFactory.get()).thenReturn(scheduledTaskState);
    when(scheduledTaskState.getBatchOperationState()).thenReturn(batchOperationState);
    when(streamProcessorContext.getScheduleService()).thenReturn(scheduleService);
    when(scheduleService.runDelayedAsync(any(), taskCaptor.capture(), any())).thenReturn(null);
    when(taskResultBuilder.build()).thenReturn(null);
  }

  private static PersistedBatchOperation createBatchOperation() {
    return new PersistedBatchOperation()
        .setKey(BATCH_OPERATION_KEY)
        .setStatus(BatchOperationStatus.CREATED)
        .setBatchOperationType(CANCEL_PROCESS_INSTANCE)
        .setInitializationSearchCursor("")
        .setInitializationSearchQueryPageSize(10)
        .setNumTotalItems(0);
  }
}
