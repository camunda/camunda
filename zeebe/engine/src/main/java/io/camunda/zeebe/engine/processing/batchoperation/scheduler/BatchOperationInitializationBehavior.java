/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import com.google.common.base.Strings;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProviderFactory;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationChunkAppender.ChunkingOutcome.BufferFull;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationChunkAppender.ChunkingOutcome.Continue;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationChunkAppender.ChunkingOutcome.FetchFailed;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationChunkAppender.ChunkingOutcome.Finished;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome.Failed;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome.NeedsRetry;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome.ReducePageSize;
import io.camunda.zeebe.engine.processing.batchoperation.scheduler.BatchOperationInitializationBehavior.InitializationOutcome.Success;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes batch operations by processing items in chunks and handling the initialization phase.
 *
 * <p>This class is responsible for:
 *
 * <ul>
 *   <li>Processing batch operation items in configurable page sizes
 *   <li>Handling chunk appending failures with retry logic and page size reduction
 *   <li>Managing the transition from initialization to execution phase
 *   <li>Recording metrics for batch operation performance
 * </ul/>
 *
 * <p>The initialization process creates an {@link ItemProvider} once per batch operation via {@link
 * ItemProviderFactory}, then fetches and processes items in pages via {@link
 * BatchOperationChunkAppender}, and builds commands through {@link BatchOperationCommands}. If
 * chunk appending fails, it attempts to reduce the page size and retry, or marks the operation as
 * failed if the minimum page size is reached.
 *
 * @see BatchOperationChunkAppender
 * @see BatchOperationCommands
 * @see ItemProviderFactory
 * @see PersistedBatchOperation
 */
public class BatchOperationInitializationBehavior {
  private static final String ERROR_MSG_BUFFER_CAPACITY_EXCEEDED =
      "Result buffer too small: cannot fit even a single item after reducing page size to minimum";
  private static final Logger LOG =
      LoggerFactory.getLogger(BatchOperationInitializationBehavior.class);

  private final ItemProviderFactory itemProviderFactory;
  private final BatchOperationMetrics metrics;
  private final BatchOperationCommands commands;
  private final BatchOperationChunkAppender chunkAppender;

  public BatchOperationInitializationBehavior(
      final ItemProviderFactory itemProviderFactory,
      final BatchOperationChunkAppender chunkAppender,
      final BatchOperationCommands commands,
      final BatchOperationMetrics metrics) {
    this.itemProviderFactory = itemProviderFactory;
    this.commands = commands;
    this.chunkAppender = chunkAppender;
    this.metrics = metrics;
  }

  /**
   * Initializes a batch operation by processing its items in pages and appending them as chunks.
   * This method handles the initialization phase of a batch operation, including:
   *
   * <ul>
   *   <li>Checking if the operation is suspended
   *   <li>Fetching items using an item provider
   *   <li>Processing pages of items and appending them as chunks
   *   <li>Handling chunk appending failures with retries and page size reduction
   *   <li>Transitioning to the execution phase once initialization is complete
   *   <li>Recording metrics for the operation
   * </ul>
   *
   * <p>This method returns an {@link InitializationOutcome} indicating success, need for retry, or
   * terminal failure. It does not throw exceptions for control flow.
   *
   * @param initialContext the initialization context (cursor, page size, etc.) built by the
   *     scheduler
   * @param taskResultBuilder the builder to append task results
   * @return the outcome of the initialization attempt
   */
  public InitializationOutcome initializeBatchOperation(
      final InitializationContext initialContext, final TaskResultBuilder taskResultBuilder) {
    final var batchOperation = initialContext.operation();

    final var itemProvider = itemProviderFactory.fromBatchOperation(batchOperation);
    var context = initialContext;

    var result = chunkAppender.fetchAndChunkNextPage(itemProvider, context, taskResultBuilder);
    while (result instanceof Continue(final var endCursor, final int itemsProcessed)) {
      context = context.withNextPage(endCursor, itemsProcessed);
      result = chunkAppender.fetchAndChunkNextPage(itemProvider, context, taskResultBuilder);
    }

    return switch (result) {
      case Finished(final var endCursor, final int itemsProcessed) -> {
        context = context.withNextPage(endCursor, itemsProcessed);
        finishInitialization(batchOperation, taskResultBuilder);
        startExecutionPhase(taskResultBuilder, context);
        yield new Success(endCursor);
      }
      case final BufferFull ignored -> handleFailedChunkAppend(taskResultBuilder, context);
      case FetchFailed(final var cause) -> {
        if (context.hasAppendedChunks()) {
          continueInitialization(taskResultBuilder, context);
        }
        yield new NeedsRetry(context.currentCursor(), cause);
      }
      default ->
          new Failed(
              "Unexpected ChunkingOutcome:" + result.getClass().getSimpleName(),
              BatchOperationErrorType.UNKNOWN);
    };
  }

  private InitializationOutcome handleFailedChunkAppend(
      final TaskResultBuilder taskResultBuilder, final InitializationContext context) {
    if (!context.hasAppendedChunks()) {
      if (context.pageSize() > 1) {
        final var reducedContext = context.withHalvedPageSize();
        continueInitialization(taskResultBuilder, reducedContext);
        return new ReducePageSize(reducedContext.pageSize());
      } else {
        return new Failed(
            ERROR_MSG_BUFFER_CAPACITY_EXCEEDED,
            BatchOperationErrorType.RESULT_BUFFER_SIZE_EXCEEDED);
      }
    } else {
      continueInitialization(taskResultBuilder, context);
      return new Success(Strings.nullToEmpty(context.currentCursor()));
    }
  }

  private void startExecutionPhase(
      final TaskResultBuilder resultBuilder, final InitializationContext context) {
    commands.appendExecutionCommand(resultBuilder, context.operation().getKey());

    metrics.recordItemsPerPartition(
        context.operation().getNumTotalItems() + context.itemsProcessed(),
        context.operation().getBatchOperationType());
    metrics.startStartExecuteLatencyMeasure(
        context.operation().getKey(), context.operation().getBatchOperationType());
    metrics.startTotalExecutionLatencyMeasure(
        context.operation().getKey(), context.operation().getBatchOperationType());
  }

  private void finishInitialization(
      final PersistedBatchOperation batchOperation, final TaskResultBuilder resultBuilder) {
    commands.appendFinishInitializationCommand(resultBuilder, batchOperation.getKey());
    metrics.recordInitialized(batchOperation.getBatchOperationType());
  }

  private void continueInitialization(
      final TaskResultBuilder taskResultBuilder, final InitializationContext context) {

    commands.appendInitializationCommand(
        taskResultBuilder,
        context.operation().getKey(),
        context.currentCursor(),
        context.pageSize());
  }

  public void appendFailedCommand(
      final TaskResultBuilder taskResultBuilder,
      final long batchOperationKey,
      final String message,
      final BatchOperationErrorType errorType) {
    commands.appendFailureCommand(taskResultBuilder, batchOperationKey, message, errorType);
  }

  /**
   * Represents the outcome of a batch operation initialization attempt.
   *
   * <p>This sealed interface provides explicit outcomes without using exceptions for control flow:
   *
   * <ul>
   *   <li>{@link Success} - initialization completed successfully or made progress
   *   <li>{@link NeedsRetry} - a transient failure occurred, retry is possible
   *   <li>{@link ReducePageSize} - buffer was full, retry with smaller page size
   *   <li>{@link Failed} - a terminal failure occurred, no retry possible
   * </ul>
   */
  public sealed interface InitializationOutcome {
    /** Initialization succeeded or made progress. Contains the cursor for the next page. */
    record Success(String cursor) implements InitializationOutcome {}

    /** A transient failure occurred. Contains the cursor to resume from and the cause. */
    record NeedsRetry(String cursor, Throwable cause) implements InitializationOutcome {}

    /**
     * Buffer was full before any chunks were appended. Retry immediately with reduced page size. No
     * command is written — the scheduler tracks the reduced page size in memory.
     */
    record ReducePageSize(int newPageSize) implements InitializationOutcome {}

    /** A terminal failure occurred. Contains the error message and type. */
    record Failed(String message, BatchOperationErrorType errorType)
        implements InitializationOutcome {}
  }
}
