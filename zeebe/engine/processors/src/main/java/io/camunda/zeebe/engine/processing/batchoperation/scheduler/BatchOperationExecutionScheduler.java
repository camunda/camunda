/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome.Failed;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome.NeedsRetry;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome.ReducePageSize;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome.Success;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationRetryPolicy.RetryDecision.Fail;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationRetryPolicy.RetryDecision.Retry;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduler responsible for executing batch operations in a distributed environment.
 *
 * <p>This class implements the {@link StreamProcessorLifecycleAware} interface to manage the
 * lifecycle of batch operation execution. It continuously polls for pending batch operations and
 * executes them with retry logic and proper error handling.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Scheduling and executing pending batch operations
 *   <li>Managing retry logic for failed operations
 *   <li>Preventing concurrent execution of the same batch operation
 *   <li>Handling initialization and re-initialization scenarios
 * </ul>
 *
 * <p><b>Design decision — serial scheduling:</b> Only one batch operation is processed at a time.
 * This is intentional for two reasons:
 *
 * <ul>
 *   <li>batch operations have lower priority than normal Zeebe record processing, so we limit their
 *       resource footprint to avoid impacting stream processing throughput; and
 *   <li>two concurrent batch operations could target overlapping process instances, leading to
 *       write conflicts on the same records. Serial execution eliminates both problems.
 * </ul>
 *
 * <p>The scheduler uses an atomic execution flag to ensure only one batch operation execution cycle
 * runs at a time, and maintains state about currently initializing operations to prevent duplicate
 * processing.
 *
 * @see StreamProcessorLifecycleAware
 * @see BatchOperationInitializationBehavior
 * @see BatchOperationRetryPolicy
 */
public class BatchOperationExecutionScheduler implements StreamProcessorLifecycleAware {
  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationExecutionScheduler.class);

  private final Duration initialPollingInterval;
  private final int defaultPageSize;
  private final BatchOperationState batchOperationState;
  private final BatchOperationInitializationBehavior batchOperationInitializer;
  private final BatchOperationRetryPolicy retryPolicy;
  private final AtomicBoolean executing = new AtomicBoolean(false);
  private final AtomicReference<SchedulerExecutionState> executionState = new AtomicReference<>();

  private ReadonlyStreamProcessorContext processingContext;

  public BatchOperationExecutionScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final BatchOperationInitializationBehavior batchOperationInitializer,
      final BatchOperationRetryPolicy retryPolicy,
      final Duration batchOperationSchedulerInterval,
      final int defaultPageSize) {

    batchOperationState = scheduledTaskStateFactory.get().getBatchOperationState();
    initialPollingInterval = batchOperationSchedulerInterval;
    this.defaultPageSize = defaultPageSize;

    this.batchOperationInitializer = batchOperationInitializer;
    this.retryPolicy = retryPolicy;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    processingContext = context;
    scheduleExecution(initialPollingInterval);
  }

  @Override
  public void onResumed() {
    scheduleExecution(initialPollingInterval);
  }

  private void scheduleExecution(final Duration nextDelay) {
    if (!executing.get()) {
      processingContext
          .getScheduleService()
          .runDelayedAsync(nextDelay, this::execute, AsyncTaskGroup.BATCH_OPERATIONS);
    } else {
      LOG.warn("Execution is already in progress, skipping scheduling.");
    }
  }

  private TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    var nextDelay = initialPollingInterval;
    try {
      LOG.trace("Looking for the next pending batch operation to execute (scheduled).");
      executing.set(true);
      final var nextPendingOperation = batchOperationState.getNextPendingBatchOperation();
      if (nextPendingOperation.isPresent()) {
        nextDelay = executeRetrying(nextPendingOperation.get(), taskResultBuilder);
      }
    } finally {
      executing.set(false);
      scheduleExecution(nextDelay);
    }
    return taskResultBuilder.build();
  }

  /**
   * Executes the batch operation with retry logic.
   *
   * <p>This method checks if the batch operation is already being initialized or executed, and if
   * not, it attempts to initialize the batch operation. If initialization fails, it appends a
   * failure command to the task result builder. If initialization succeeds, it updates the state
   * and returns the initial polling interval for the next execution. If a retry is needed, it
   * returns the delay for the next execution based on the retry context.
   *
   * <p>This method is called by the scheduler to process batch operations in a controlled manner,
   * ensuring that operations are not re-initialized unnecessarily and that retries are handled
   * appropriately.
   *
   * @param batchOperation the batch operation to execute
   * @param taskResultBuilder the task result builder to append results to
   * @return the delay for the next execution
   */
  private Duration executeRetrying(
      final PersistedBatchOperation batchOperation, final TaskResultBuilder taskResultBuilder) {

    final var currentState = getOrCreateState(batchOperation);
    if (currentState.shouldSkipInitialization(batchOperation)) {
      LOG.trace(
          "Batch operation {} initialization already in progress, skipping.",
          batchOperation.getKey());
      return initialPollingInterval;
    }

    final var context = InitializationContext.fromBatchOperation(batchOperation, defaultPageSize);
    final var outcome =
        batchOperationInitializer.initializeBatchOperation(context, taskResultBuilder);

    return switch (outcome) {
      case Success(final var cursor) -> {
        executionState.set(currentState.advanceTo(cursor));
        yield initialPollingInterval;
      }
      case Failed(final var message, final var errorType) -> {
        batchOperationInitializer.appendFailedCommand(
            taskResultBuilder, batchOperation.getKey(), message, errorType);
        yield initialPollingInterval;
      }
      case ReducePageSize(final int newPageSize) -> {
        LOG.debug(
            "Buffer full for batch operation {}, reducing page size to {}",
            batchOperation.getKey(),
            newPageSize);
        executionState.set(currentState.withReducedPageSize(newPageSize));
        yield initialPollingInterval;
      }
      case NeedsRetry(final var cursor, final var cause) ->
          handleRetry(taskResultBuilder, batchOperation, currentState, cursor, cause);
    };
  }

  private Duration handleRetry(
      final TaskResultBuilder taskResultBuilder,
      final PersistedBatchOperation batchOperation,
      final SchedulerExecutionState currentState,
      final String cursor,
      final Throwable cause) {

    final var decision = retryPolicy.evaluate(cursor, cause, currentState.numAttempts());

    return switch (decision) {
      case Fail(final var message, final var errorType) -> {
        batchOperationInitializer.appendFailedCommand(
            taskResultBuilder, batchOperation.getKey(), message, errorType);
        yield initialPollingInterval;
      }
      case Retry(final var delay, final int numAttempts, final String retryCursor) -> {
        LOG.warn(
            "Retryable operation failed, retries left: {}, retrying in {} ms",
            retryPolicy.getMaxRetries() - numAttempts,
            delay.toMillis());
        executionState.set(currentState.retryWith(retryCursor, numAttempts));
        yield delay;
      }
    };
  }

  /**
   * Returns existing execution state or creates fresh state from the batch operation.
   *
   * <p>We reset state when:
   *
   * <ul>
   *   <li>No state exists (first run), or
   *   <li>The batch operation key changed (switched to a different BO)
   * </ul>
   */
  private SchedulerExecutionState getOrCreateState(final PersistedBatchOperation batchOperation) {
    var state = executionState.get();
    if (state == null || state.batchOperationKey() != batchOperation.getKey()) {
      state = SchedulerExecutionState.from(batchOperation, defaultPageSize);
      executionState.set(state);
    }
    return state;
  }
}
