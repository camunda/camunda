/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import com.google.common.base.Strings;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationRetryHandler.RetryResult.Failure;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationRetryHandler.RetryResult.Retry;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationRetryHandler.RetryResult.Success;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.Objects;
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
 * <p>The scheduler uses an atomic execution flag to ensure only one batch operation execution cycle
 * runs at a time, and maintains state about currently initializing operations to prevent duplicate
 * processing.
 *
 * @see StreamProcessorLifecycleAware
 * @see BatchOperationInitializer
 * @see BatchOperationRetryHandler
 */
public class BatchOperationExecutionScheduler implements StreamProcessorLifecycleAware {
  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationExecutionScheduler.class);

  private final Duration initialPollingInterval;
  private final BatchOperationState batchOperationState;
  private final BatchOperationInitializer batchOperationInitializer;
  private final BatchOperationRetryHandler retryHandler;
  private final AtomicBoolean executing = new AtomicBoolean(false);
  private final AtomicReference<ExecutionLoopState> initializing = new AtomicReference<>();

  private ReadonlyStreamProcessorContext processingContext;

  public BatchOperationExecutionScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final BatchOperationInitializer batchOperationInitializer,
      final BatchOperationRetryHandler retryHandler,
      final Duration batchOperationSchedulerInterval) {

    batchOperationState = scheduledTaskStateFactory.get().getBatchOperationState();
    initialPollingInterval = batchOperationSchedulerInterval;

    this.batchOperationInitializer = batchOperationInitializer;
    this.retryHandler = retryHandler;
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

    if (!validateNoReInitialization(batchOperation)) {
      return initialPollingInterval;
    }

    final var retryResult =
        retryHandler.executeWithRetry(
            () ->
                batchOperationInitializer.initializeBatchOperation(
                    batchOperation, taskResultBuilder),
            initializing.get().numAttempts);

    return switch (retryResult) {
      case Success(final var cursor) -> {
        initializing.set(new ExecutionLoopState(batchOperation.getKey(), cursor, 0));
        yield initialPollingInterval;
      }
      case Failure(final var exception) -> {
        batchOperationInitializer.appendFailedCommand(
            taskResultBuilder, batchOperation.getKey(), exception);
        yield initialPollingInterval;
      }
      case Retry(final var delay, final int numAttempts, final String endCursor) -> {
        LOG.warn(
            "Retryable operation failed, retries left: {}, retrying in {} ms",
            retryHandler.getMaxRetries() - numAttempts,
            delay.toMillis());
        initializing.set(new ExecutionLoopState(batchOperation.getKey(), endCursor, numAttempts));
        yield delay;
      }
    };
  }

  private boolean validateNoReInitialization(final PersistedBatchOperation batchOperation) {
    final var initializingBO = initializing.get();
    if (initializingBO != null
        && initializingBO.batchOperationKey == batchOperation.getKey()
        && !Objects.equals(
            initializingBO.searchResultCursor, batchOperation.getInitializationSearchCursor())
        && initializingBO.numAttempts == 0) {
      LOG.trace(
          "Batch operation {} is already being executed, skipping re-initialization.",
          batchOperation.getKey());
      return false;
    } else if (initializingBO == null) {
      initializing.set(new ExecutionLoopState(batchOperation));
    }
    return true;
  }

  record ExecutionLoopState(long batchOperationKey, String searchResultCursor, int numAttempts) {
    public ExecutionLoopState(final PersistedBatchOperation batchOperation) {
      this(
          batchOperation.getKey(),
          Strings.nullToEmpty(batchOperation.getInitializationSearchCursor()),
          0);
    }
  }
}
