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
 * @see BatchOperationRetryHandler
 */
public class BatchOperationExecutionScheduler implements StreamProcessorLifecycleAware {
  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationExecutionScheduler.class);

  private final Duration initialPollingInterval;
  private final BatchOperationState batchOperationState;
  private final BatchOperationInitializationBehavior batchOperationInitializer;
  private final BatchOperationRetryHandler retryHandler;
  private final AtomicBoolean executing = new AtomicBoolean(false);
  private final AtomicReference<ExecutionLoopState> executionState = new AtomicReference<>();

  private ReadonlyStreamProcessorContext processingContext;

  public BatchOperationExecutionScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final BatchOperationInitializationBehavior batchOperationInitializer,
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

    final var currentState = getOrCreateState(batchOperation);
    if (currentState.shouldSkipInitialization(batchOperation)) {
      LOG.trace(
          "Batch operation {} initialization already in progress, skipping.",
          batchOperation.getKey());
      return initialPollingInterval;
    }

    final var retryResult =
        retryHandler.executeWithRetry(
            () ->
                batchOperationInitializer.initializeBatchOperation(
                    batchOperation, taskResultBuilder),
            currentState.numAttempts());

    return switch (retryResult) {
      case Success(final var cursor) -> {
        executionState.set(currentState.advanceTo(cursor));
        yield initialPollingInterval;
      }
      case Failure(final var message, final var errorType) -> {
        batchOperationInitializer.appendFailedCommand(
            taskResultBuilder, batchOperation.getKey(), message, errorType);
        yield initialPollingInterval;
      }
      case Retry(final var delay, final int numAttempts, final String endCursor) -> {
        LOG.warn(
            "Retryable operation failed, retries left: {}, retrying in {} ms",
            retryHandler.getMaxRetries() - numAttempts,
            delay.toMillis());
        executionState.set(currentState.retryWith(endCursor, numAttempts));
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
  private ExecutionLoopState getOrCreateState(final PersistedBatchOperation batchOperation) {
    var state = executionState.get();
    if (state == null || state.batchOperationKey != batchOperation.getKey()) {
      state = ExecutionLoopState.from(batchOperation);
      executionState.set(state);
    }
    return state;
  }

  /**
   * Tracks the in-flight state of a batch operation being initialized.
   *
   * <p>This class exists to prevent re-initialization of a batch operation before the stream
   * processor has processed our previous INITIALIZE command. Without this guard, the scheduler
   * could fire multiple INITIALIZE commands for the same cursor position, causing duplicate work.
   *
   * <p>The key invariant: after we successfully initialize a page and append an INITIALIZE command,
   * the persisted state's cursor will be updated asynchronously by the command processor. Until
   * that happens, our in-memory cursor differs from the persisted cursor. We use this difference to
   * detect "command in flight" and skip re-initialization.
   *
   * <p>However, if retries are in flight ({@code numAttempts > 0}), we should NOT skip — retries
   * are intentional re-executions after transient failures.
   */
  static final class ExecutionLoopState {
    private final long batchOperationKey;
    private final String lastKnownCursor;
    private final int numAttempts;

    private ExecutionLoopState(
        final long batchOperationKey, final String lastKnownCursor, final int numAttempts) {
      this.batchOperationKey = batchOperationKey;
      this.lastKnownCursor = lastKnownCursor;
      this.numAttempts = numAttempts;
    }

    /** Creates initial state from a persisted batch operation. */
    static ExecutionLoopState from(final PersistedBatchOperation batchOperation) {
      return new ExecutionLoopState(
          batchOperation.getKey(),
          Strings.nullToEmpty(batchOperation.getInitializationSearchCursor()),
          0);
    }

    /** Advances to a new cursor after successful page processing. Resets retry count. */
    ExecutionLoopState advanceTo(final String newCursor) {
      return new ExecutionLoopState(batchOperationKey, newCursor, 0);
    }

    /** Prepares for a retry attempt with the given cursor and attempt count. */
    ExecutionLoopState retryWith(final String cursor, final int attempts) {
      return new ExecutionLoopState(batchOperationKey, cursor, attempts);
    }

    /**
     * Determines whether initialization should be skipped for the given batch operation.
     *
     * <p>Precondition: This method assumes the state tracks the same batch operation as the one
     * passed in (same key). This is guaranteed by {@link
     * BatchOperationExecutionScheduler#getOrCreateState}, which resets state when the key changes.
     *
     * <p>We skip if BOTH of the following are true:
     *
     * <ul>
     *   <li>Our in-memory cursor differs from the persisted cursor — this means we already appended
     *       an INITIALIZE command that advanced our cursor, but the stream processor has NOT yet
     *       processed it to update the persisted state. We're "ahead" and waiting for it to catch
     *       up.
     *   <li>No retries are in flight ({@code numAttempts == 0}) — retries are intentional
     *       re-executions after transient failures and should proceed even if cursors differ.
     * </ul>
     *
     * <p>Once the stream processor processes our command, the persisted cursor will match our
     * in-memory cursor, and the next scheduler run will proceed (cursors equal → don't skip).
     */
    boolean shouldSkipInitialization(final PersistedBatchOperation batchOperation) {
      return batchOperationKey == batchOperation.getKey()
          && !Objects.equals(lastKnownCursor, batchOperation.getInitializationSearchCursor())
          && numAttempts == 0;
    }

    int numAttempts() {
      return numAttempts;
    }
  }
}
