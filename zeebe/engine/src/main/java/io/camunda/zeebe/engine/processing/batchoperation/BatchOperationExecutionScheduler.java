/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import com.google.common.base.Strings;
import com.google.common.math.IntMath;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProviderFactory;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The is class is a scheduler that periodically checks for newly created batch operations and
 * initializes them with the itemKeys to be executed.
 *
 * <p>For this, it deserializes the filter object and queries the EntityKeyProvider for all matching
 * itemKeys for this partition. Then this collection of itemKeys will be split into smaller chunks
 * and appended to the TaskResultBuilder as BatchOperationChunkRecord.
 */
public class BatchOperationExecutionScheduler implements StreamProcessorLifecycleAware {

  public static final String ERROR_MSG_FAILED_FIRST_CHUNK_APPEND =
      "Unable to append first chunk of batch operation items. Number of items: %d";
  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationExecutionScheduler.class);
  private static final Set<Reason> FAIL_IMMEDIATELY_REASONS =
      Set.of(
          Reason.NOT_FOUND, Reason.NOT_UNIQUE, Reason.SECONDARY_STORAGE_NOT_SET, Reason.FORBIDDEN);
  private final Duration initialPollingInterval;
  private final Duration initialRetryDelay;
  private final Duration maxRetryDelay;
  private final int maxRetries;
  private final int backoffFactor;

  private final BatchOperationState batchOperationState;
  private ReadonlyStreamProcessorContext processingContext;
  private final BatchOperationInitializer batchOperationInitializer;

  /** Marks if this scheduler is currently executing or not. */
  private final AtomicBoolean executing = new AtomicBoolean(false);

  private final AtomicReference<ExecutionLoopState> initializing = new AtomicReference<>();

  public BatchOperationExecutionScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final ItemProviderFactory itemProviderFactory,
      final EngineConfiguration engineConfiguration,
      final int partitionId,
      final BatchOperationMetrics metrics) {
    batchOperationState = scheduledTaskStateFactory.get().getBatchOperationState();
    initialPollingInterval = engineConfiguration.getBatchOperationSchedulerInterval();
    initialRetryDelay = engineConfiguration.getBatchOperationQueryRetryInitialDelay();
    maxRetryDelay = engineConfiguration.getBatchOperationQueryRetryMaxDelay();
    maxRetries = engineConfiguration.getBatchOperationQueryRetryMax();
    backoffFactor = engineConfiguration.getBatchOperationQueryRetryBackoffFactor();
    batchOperationInitializer =
        new BatchOperationInitializer(
            itemProviderFactory, engineConfiguration, partitionId, metrics);
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

  /** Schedules the next execution of the batch operation scheduler run. */
  private void scheduleExecution(final Duration nextDelay) {
    if (!executing.get()) {
      processingContext
          .getScheduleService()
          .runDelayedAsync(nextDelay, this::execute, AsyncTaskGroup.BATCH_OPERATIONS);
    } else {
      LOG.warn("Execution is already in progress, skipping scheduling.");
    }
  }

  /**
   * Executes the next pending batch operation in the queue. If more than one batch operation is
   * pending, the following one will be executed in the next scheduled run.
   *
   * @param taskResultBuilder the task result builder to append the commands to
   * @return the task result containing the commands to be executed
   */
  private TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    var nextDelay = initialPollingInterval;
    try {
      LOG.trace("Looking for the next pending batch operation to execute (scheduled).");
      executing.set(true);
      nextDelay =
          executeRetrying(batchOperationState.getNextPendingBatchOperation(), taskResultBuilder);
    } finally {
      executing.set(false);
      scheduleExecution(nextDelay);
    }
    return taskResultBuilder.build();
  }

  private Duration executeRetrying(
      final Optional<PersistedBatchOperation> batchOperation,
      final TaskResultBuilder taskResultBuilder) {
    if (batchOperation.isEmpty()) {
      return initialPollingInterval;
    }

    if (!validateNoReInitialization(batchOperation.get())) {
      return initialPollingInterval;
    }

    final var result =
        batchOperationInitializer.initializeBatchOperation(batchOperation.get(), taskResultBuilder);
    if (!result.isSuccess()) {
      if (shouldFailImmediately(result.exception())
          || initializing.get().numAttempts >= maxRetries) {
        batchOperationInitializer.appendFailedCommand(
            taskResultBuilder,
            batchOperation.get(),
            ExceptionUtils.getStackTrace(result.exception()),
            BatchOperationErrorType.QUERY_FAILED);
        return initialPollingInterval;
      }
      final var calculatedRetryDelay =
          initialRetryDelay.multipliedBy(
              IntMath.pow(backoffFactor, initializing.get().numAttempts()));
      final var nextRetryDelay =
          maxRetryDelay.compareTo(calculatedRetryDelay) < 0 ? maxRetryDelay : calculatedRetryDelay;
      LOG.warn(
          "Retryable operation failed, retries left: {}, retrying in {} ms. Error: {}",
          initializing.get().numAttempts(),
          nextRetryDelay,
          result.exception().getLocalizedMessage());
      initializing.set(
          new ExecutionLoopState(
              initializing.get().batchOperationKey,
              Strings.nullToEmpty(initializing.get().searchResultCursor),
              initializing.get().numAttempts() + 1));
      return nextRetryDelay;
    } else {
      initializing.set(
          new ExecutionLoopState(result.batchOperationKey(), result.searchResultCursor(), 0));
    }

    return initialPollingInterval;
  }

  private boolean shouldFailImmediately(final Exception exception) {
    return exception instanceof CamundaSearchException
        && FAIL_IMMEDIATELY_REASONS.contains(((CamundaSearchException) exception).getReason());
  }

  private boolean validateNoReInitialization(final PersistedBatchOperation batchOperation) {
    final var initializingBO = initializing.get();
    if (initializingBO != null
        && initializingBO.batchOperationKey == batchOperation.getKey()
        && !Objects.equals(
            initializingBO.searchResultCursor, batchOperation.getInitializationSearchCursor())
        && initializingBO.numAttempts == 0) {
      // If the batch operation is already being initialized, we do not re-initialize it.
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
      this(batchOperation.getKey(), batchOperation.getInitializationSearchCursor(), 0);
    }
  }
}
