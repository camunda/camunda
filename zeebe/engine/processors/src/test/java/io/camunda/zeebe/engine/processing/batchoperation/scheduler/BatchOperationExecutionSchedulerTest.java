/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static io.camunda.zeebe.protocol.record.value.BatchOperationType.CANCEL_PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationRetryPolicy.RetryDecision;
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
  private static final int DEFAULT_PAGE_SIZE = 100;

  @Mock private Supplier<ScheduledTaskState> scheduledTaskStateFactory;
  @Mock private TaskResultBuilder taskResultBuilder;
  @Mock private ReadonlyStreamProcessorContext streamProcessorContext;
  @Mock private ProcessingScheduleService scheduleService;

  @Mock private BatchOperationState batchOperationState;
  @Mock private BatchOperationInitializationBehavior batchOperationInitializer;
  @Mock private BatchOperationRetryPolicy retryPolicy;

  @Captor private ArgumentCaptor<Task> taskCaptor;

  private BatchOperationExecutionScheduler scheduler;

  @BeforeEach
  public void setUp() {
    setUpBasicSchedulerBehaviour();

    scheduler =
        new BatchOperationExecutionScheduler(
            scheduledTaskStateFactory,
            batchOperationInitializer,
            retryPolicy,
            SCHEDULER_INTERVAL,
            DEFAULT_PAGE_SIZE);
  }

  @Test
  public void shouldSkipWhenBatchOperationIsSuspended() {
    // given
    final var batchOperation = createBatchOperation().setStatus(BatchOperationStatus.SUSPENDED);
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    // when
    execute();

    // then - initializer should not be called for suspended operations
    verifyNoInteractions(batchOperationInitializer, retryPolicy);
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
  }

  @Test
  public void shouldDoNothingWhenNoBatchOperation() {
    // given
    when(batchOperationState.getNextPendingBatchOperation()).thenReturn(Optional.empty());

    // when
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verifyNoInteractions(batchOperationInitializer, retryPolicy);
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
  }

  @Test
  public void shouldCallInitializerDirectlyWhenBatchOperationExists() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.Success("cursor"));

    // when
    execute();

    // then
    verify(batchOperationState).getNextPendingBatchOperation();
    verify(batchOperationInitializer).initializeBatchOperation(any(), eq(taskResultBuilder));
    verifyNoInteractions(retryPolicy);
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
  }

  @Test
  public void shouldHandleInitializationSuccess() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.Success("new-cursor"));

    // when
    execute();

    // then
    verify(batchOperationInitializer).initializeBatchOperation(any(), eq(taskResultBuilder));
    verifyNoInteractions(retryPolicy);
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
  }

  @Test
  public void shouldHandleInitializationFailed() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    final var errorMessage = "Test error";
    final var errorType = BatchOperationErrorType.QUERY_FAILED;
    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.Failed(errorMessage, errorType));

    // when
    execute();

    // then
    verify(batchOperationInitializer).initializeBatchOperation(any(), eq(taskResultBuilder));
    verify(batchOperationInitializer)
        .appendFailedCommand(taskResultBuilder, BATCH_OPERATION_KEY, errorMessage, errorType);
    verifyNoInteractions(retryPolicy);
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
  }

  @Test
  public void shouldReducePageSizeAndRetryOnNextSchedulerTick() {
    // given - two separate instances: first has original page size, second has reduced
    // (simulating the continueInitialization command being processed between ticks)
    final var bo1 = createBatchOperation();
    final int reducedPageSize = 50;
    final var bo2 = createBatchOperation().setInitializationSearchQueryPageSize(reducedPageSize);

    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(bo1))
        .thenReturn(Optional.of(bo2));

    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.ReducePageSize(reducedPageSize))
        .thenReturn(new InitializationOutcome.Success("cursor"));

    // when - first execution returns ReducePageSize, second succeeds after persisted state caught
    // up
    execute();
    execute();

    // then - initializer should be called twice
    final var contextCapture = ArgumentCaptor.forClass(InitializationContext.class);
    verify(batchOperationInitializer, times(2))
        .initializeBatchOperation(contextCapture.capture(), any());

    assertThat(contextCapture.getAllValues().getLast().pageSize()).isEqualTo(reducedPageSize);
  }

  @Test
  public void shouldDelegateToRetryHandlerOnNeedsRetry() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    final var cause = new RuntimeException("Transient failure");
    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.NeedsRetry("cursor", cause));

    final var retryDelay = Duration.ofMillis(500);
    when(retryPolicy.evaluate(eq("cursor"), eq(cause), eq(0)))
        .thenReturn(RetryDecision.retry(retryDelay, 1, "cursor"));

    // when
    execute();

    // then
    verify(batchOperationInitializer).initializeBatchOperation(any(), eq(taskResultBuilder));
    verify(retryPolicy).evaluate("cursor", cause, 0);
    verify(scheduleService)
        .runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any()); // initial schedule
    verify(scheduleService).runDelayedAsync(eq(retryDelay), any(), any()); // retry schedule
  }

  @Test
  public void shouldHandleRetryDecisionFail() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    final var cause = new RuntimeException("Non-retryable failure");
    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.NeedsRetry("cursor", cause));

    final var errorMessage = "Failed after retries";
    final var errorType = BatchOperationErrorType.QUERY_FAILED;
    when(retryPolicy.evaluate(any(), any(), anyInt()))
        .thenReturn(RetryDecision.fail(errorMessage, errorType));

    // when
    execute();

    // then
    verify(retryPolicy).evaluate("cursor", cause, 0);
    verify(batchOperationInitializer)
        .appendFailedCommand(taskResultBuilder, BATCH_OPERATION_KEY, errorMessage, errorType);
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
  }

  @Test
  public void shouldSkipWhenCommandInFlight() {
    // given - first execution advances in-memory cursor to "cursor1"
    // second execution sees persisted cursor changed to "cursor2" (command was processed)
    // but our in-memory cursor is still "cursor1" (different from persisted)
    // this indicates a command is in-flight, so we skip
    final var batchOperation = createBatchOperation().setInitializationSearchCursor("cursor1");
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation))
        .thenReturn(Optional.of(batchOperation.setInitializationSearchCursor("cursor2")));

    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.Success("cursor1"));

    // when - execute twice
    execute();
    execute();

    // then - should only process the first one, skip the second (command in-flight)
    verify(batchOperationInitializer, times(1)).initializeBatchOperation(any(), any());
  }

  @Test
  public void shouldContinueWhenPersistedStateCaughtUp() {
    // given - both executions see the same cursor (persisted state caught up with in-memory)
    final var batchOperation = createBatchOperation().setInitializationSearchCursor("cursor1");
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation))
        .thenReturn(Optional.of(batchOperation));

    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.Success("cursor1"));

    // when - execute twice
    execute();
    execute();

    // then - should process both since cursors match (no command in-flight)
    verify(batchOperationInitializer, times(2)).initializeBatchOperation(any(), any());
  }

  @Test
  public void shouldProcessNewBatchOperationAfterDifferentKey() {
    // given
    final var batchOperation1 = createBatchOperation();
    final var batchOperation2 = createBatchOperation().setKey(BATCH_OPERATION_KEY + 1);

    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation1))
        .thenReturn(Optional.of(batchOperation2));

    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.Success("cursor1"))
        .thenReturn(new InitializationOutcome.Success("cursor2"));

    // when
    execute();
    execute();

    // then - should process both operations
    verify(batchOperationInitializer, times(2)).initializeBatchOperation(any(), any());
  }

  @Test
  public void shouldRescheduleWithInitialIntervalAfterProcessing() {
    // given
    final var batchOperation = createBatchOperation();
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.Success("cursor"));

    // when
    execute();

    // then
    verify(scheduleService, times(2)).runDelayedAsync(eq(SCHEDULER_INTERVAL), any(), any());
  }

  @Test
  public void shouldPassCorrectNumAttemptsToRetryHandlerOnSubsequentRetries() {
    // given - NeedsRetry with same cursor (no continueInit command written, e.g. fetch failed
    // before any chunks appended) so retries proceed without waiting for command processing
    final var batchOperation = createBatchOperation().setInitializationSearchCursor("cursor1");
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation));

    final var cause = new RuntimeException("Transient failure");
    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.NeedsRetry("cursor1", cause));

    // First call returns retry with numAttempts=1, second call returns retry with numAttempts=2
    when(retryPolicy.evaluate(any(), any(), eq(0)))
        .thenReturn(RetryDecision.retry(Duration.ofMillis(100), 1, "cursor1"));
    when(retryPolicy.evaluate(any(), any(), eq(1)))
        .thenReturn(RetryDecision.retry(Duration.ofMillis(200), 2, "cursor1"));

    // when - execute twice (simulating retry)
    execute();
    execute();

    // then - verify numAttempts increments
    verify(retryPolicy).evaluate("cursor1", cause, 0); // first execution
    verify(retryPolicy).evaluate("cursor1", cause, 1); // second execution after retry
  }

  @Test
  public void shouldSkipRetryWhenCommandInFlight() {
    // given - batch operation with cursor "cursor1"
    final var batchOperation = createBatchOperation().setInitializationSearchCursor("cursor1");
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation))
        // Second call: cursor still "cursor1" (command not yet processed)
        .thenReturn(Optional.of(batchOperation));

    final var cause = new RuntimeException("Transient failure");
    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.NeedsRetry("cursor2", cause));

    // First retry decision advances cursor to "cursor2" with numAttempts=1
    when(retryPolicy.evaluate(any(), any(), eq(0)))
        .thenReturn(RetryDecision.retry(Duration.ofMillis(100), 1, "cursor2"));

    // when - execute twice: first initializes, second should skip (command in-flight)
    execute();
    execute();

    // then - only one initialization (second tick skipped because persisted cursor
    // hasn't caught up, indicating the engine is still processing the previous command)
    verify(batchOperationInitializer, times(1)).initializeBatchOperation(any(), any());
  }

  @Test
  public void shouldProceedWithRetryAfterCommandProcessed() {
    // given - batch operation with cursor "cursor1"
    final var batchOperation = createBatchOperation().setInitializationSearchCursor("cursor1");
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation))
        // Second call: cursor advanced to "cursor2" (command was processed)
        .thenReturn(Optional.of(batchOperation.setInitializationSearchCursor("cursor2")));

    final var cause = new RuntimeException("Transient failure");
    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.NeedsRetry("cursor2", cause));

    when(retryPolicy.evaluate(any(), any(), eq(0)))
        .thenReturn(RetryDecision.retry(Duration.ofMillis(100), 1, "cursor2"));
    when(retryPolicy.evaluate(any(), any(), eq(1)))
        .thenReturn(
            RetryDecision.fail("Max retries exceeded", BatchOperationErrorType.QUERY_FAILED));

    // when - first execution triggers retry, second proceeds because persisted state caught up
    execute();
    execute();

    // then - both executions should call initializer
    verify(batchOperationInitializer, times(2)).initializeBatchOperation(any(), any());
  }

  @Test
  public void shouldRetryImmediatelyWhenNoContinueCommandWritten() {
    // given - NeedsRetry with same cursor (no chunks were appended, no command written)
    final var batchOperation = createBatchOperation().setInitializationSearchCursor("cursor1");
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(batchOperation))
        .thenReturn(Optional.of(batchOperation)); // cursor unchanged — no command to process

    final var cause = new RuntimeException("Fetch failed, no chunks appended");
    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.NeedsRetry("cursor1", cause));

    when(retryPolicy.evaluate(any(), any(), eq(0)))
        .thenReturn(RetryDecision.retry(Duration.ofMillis(100), 1, "cursor1"));
    when(retryPolicy.evaluate(any(), any(), eq(1)))
        .thenReturn(
            RetryDecision.fail("Max retries exceeded", BatchOperationErrorType.QUERY_FAILED));

    // when - execute twice
    execute();
    execute();

    // then - both calls proceed: cursors match (no command in-flight)
    verify(batchOperationInitializer, times(2)).initializeBatchOperation(any(), any());
  }

  @Test
  public void shouldResetNumAttemptsOnSuccess() {
    // given - three separate instances to avoid mutable cursor aliasing
    final var bo1 = createBatchOperation().setInitializationSearchCursor("cursor1");
    final var bo2 = createBatchOperation().setInitializationSearchCursor("cursor2");
    final var bo3 = createBatchOperation().setInitializationSearchCursor("cursor3");
    when(batchOperationState.getNextPendingBatchOperation())
        .thenReturn(Optional.of(bo1))
        .thenReturn(Optional.of(bo2))
        .thenReturn(Optional.of(bo3));

    final var cause = new RuntimeException("Transient failure");
    // First call: needs retry (numAttempts goes to 1)
    // Second call: success (numAttempts should reset to 0)
    // Third call: needs retry again (should use numAttempts=0, not 1)
    when(batchOperationInitializer.initializeBatchOperation(any(), any()))
        .thenReturn(new InitializationOutcome.NeedsRetry("cursor2", cause))
        .thenReturn(new InitializationOutcome.Success("cursor3"))
        .thenReturn(new InitializationOutcome.NeedsRetry("cursor4", cause));

    when(retryPolicy.evaluate(any(), any(), eq(0)))
        .thenReturn(RetryDecision.retry(Duration.ofMillis(100), 1, "cursor2"));

    // when - first needs retry, second succeeds (persisted caught up), third needs retry again
    execute();
    execute();
    execute();

    // then - retry policy should be called twice, both times with numAttempts=0
    // (proving that success reset the counter)
    verify(retryPolicy, times(2)).evaluate(any(), any(), eq(0));
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
